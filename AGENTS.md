# Agent Instructions

## Protected Directories

**Do not modify files in the following directories without explicit user request:**

- `src/main/java/net/sf/saxon/` - Saxon library source code (upstream fork)

This is a fork of the Saxon XSLT processor. Changes to the core Saxon source code should only be made when explicitly requested by the user, as they may complicate future upstream merges.

## Safe to Modify

- `tools/saxx/` - The saxx CLI tool built on top of Saxon
- `src/test/` - Test files
- Project configuration files (pom.xml, tasks.py, etc.)
