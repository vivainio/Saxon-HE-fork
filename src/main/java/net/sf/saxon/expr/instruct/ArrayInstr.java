////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.Operand;
import net.sf.saxon.expr.OperandRole;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.elab.Elaborator;
import net.sf.saxon.expr.elab.ItemElaborator;
import net.sf.saxon.expr.elab.ItemEvaluator;
import net.sf.saxon.expr.elab.PullEvaluator;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.ma.arrays.ArrayItemType;
import net.sf.saxon.ma.arrays.SimpleArrayItem;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.om.*;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.str.Twine8;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.value.StringValue;

import java.util.ArrayList;
import java.util.List;


/**
 * An xsl:array instruction in the stylesheet (XSLT 4.0 addition)
 */

public class ArrayInstr extends Instruction {

    private final Operand selectOp;

    /**
     * Create a compiled xsl:array instruction
     *
     * @param select the expression in the select attribute or sequence constructor
     */

    public ArrayInstr(Expression select) {
        selectOp = new Operand(this, select, OperandRole.ABSORB);
    }


    public Expression getSelectExp() {
        return selectOp.getChildExpression();
    }

    public void setSelectExp(Expression nameExp) {
        selectOp.setChildExpression(nameExp);
    }

    @Override
    public Iterable<Operand> operands() {
        return operandList(selectOp);
    }

    /**
     * Get the name of this instruction for diagnostic and tracing purposes
     *
     * @return the string "xsl:processing-instruction"
     */

    @Override
    public int getInstructionNameCode() {
        return StandardNames.XSL_ARRAY;
    }

    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        return ArrayItemType.ANY_ARRAY_TYPE;
    }

    @Override
    public int getCardinality() {
        return StaticProperty.EXACTLY_ONE;
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @param rebindings variables to be re-bound
     * @return the copy of the original expression
     */

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        ArrayInstr exp = new ArrayInstr(getSelectExp().copy(rebindings));
        ExpressionTool.copyLocationInfo(this, exp);
        return exp;
    }

    @Override
    public int getDependencies() {
        return getSelectExp().getDependencies() | super.getDependencies();
    }


    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("xslArray", this);
        getSelectExp().export(out);
        out.endElement();
    }


    /**
     * Make an elaborator for this expression
     *
     * @return a suitable elaborator
     */

    @Override
    public Elaborator getElaborator() {
        return new ArrayInstrElaborator();
    }


    private static class ArrayInstrElaborator extends ItemElaborator {

        @Override
        public ItemEvaluator elaborateForItem() {
            ArrayInstr expr = (ArrayInstr) getExpression();
            Location loc = expr.getLocation();
            PullEvaluator select = expr.getSelectExp().makeElaborator().elaborateForPull();
            return context -> {
                SequenceIterator input = select.iterate(context);
                Item first = input.next();
                if (first == null) {
                    return SimpleArrayItem.EMPTY_ARRAY;
                }
                switch (first.getGenre()) {
                    case ATOMIC: {
                        List<GroundedValue> members = new ArrayList<>();
                        members.add(first);
                        for (Item item; (item = input.next()) != null; ) {
                            if (item.getGenre() == Genre.ATOMIC) {
                                members.add(item);
                            } else {
                                throw new XPathException("xsl:array: if the first item is atomic, then all must be atomic")
                                        .withErrorCode("XTTE4045")
                                        .withLocation(loc)
                                        .asTypeError();
                            }
                        }
                        return new SimpleArrayItem(members);
                    }
                    case NODE: {
                        List<GroundedValue> members = new ArrayList<>();
                        members.add(first);
                        for (Item item; (item = input.next()) != null; ) {
                            if (item.getGenre() == Genre.NODE) {
                                members.add(item);
                            } else {
                                throw new XPathException("xsl:array: if the first item is a node, then all must be nodes")
                                        .withErrorCode("XTTE4045")
                                        .withLocation(loc)
                                        .asTypeError();
                            }
                        }
                        return new SimpleArrayItem(members);
                    }
                    case MAP: {
                        List<GroundedValue> members = new ArrayList<>();
                        GroundedValue firstMember = memberFromValueRecord(first);
                        members.add(firstMember);
                        for (Item item; (item = input.next()) != null; ) {
                            members.add(memberFromValueRecord(item));
                        }
                        return new SimpleArrayItem(members);
                    }
//                    case ARRAY: {
//                        List<GroundedValue> members = new ArrayList<>();
//                        GroundedValue firstMember = memberFromArray(first);
//                        members.add(firstMember);
//                        for (Item item; (item = input.next()) != null; ) {
//                            members.add(memberFromArray(item));
//                        }
//                        return new SimpleArrayItem(members);
//                    }
                    default:
                        throw new XPathException("Invalid item in result of xsl:array/@select: " + Err.depict(first))
                                .withErrorCode("XTTE4045")
                                .withLocation(loc)
                                .asTypeError();

                }
            };
        }

        public static final StringValue valueField = new StringValue(new Twine8("value"));

        private GroundedValue memberFromValueRecord(Item valueRecord) throws XPathException {
            if (valueRecord instanceof MapItem) {
                GroundedValue val = ((MapItem) valueRecord).get(valueField);
                if (val != null) {
                    return val;
                }
            }
            throw new XPathException("In input to xsl:array, expected a value record. Found " + Err.depict(valueRecord));
        }

//        private GroundedValue memberFromArray(Item array) throws XPathException {
//            if (array instanceof ArrayItem) {
//                GroundedValue val = ((ArrayItem) array).get(valueField);
//                if (val != null) {
//                    return val;
//                }
//            }
//            throw new XPathException("In input to xsl:array, expected a value record. Found " + Err.depict(valueRecord));
//        }


    }


}

