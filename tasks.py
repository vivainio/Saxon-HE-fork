"""Task runner for Saxon-HE"""

import subprocess
import sys
import shutil
from pathlib import Path


def c(cmd: str) -> None:
    """Run command, fail on error"""
    print(f"+ {cmd}")
    subprocess.run(cmd, shell=True, check=True)


def do_build(args) -> None:
    """Build Saxon-HE JAR file"""
    c("mvn package -DskipTests")


def do_clean(args) -> None:
    """Remove build artifacts"""
    c("mvn clean")


def main():
    tasks = {name[3:]: func for name, func in globals().items() if name.startswith("do_")}
    if len(sys.argv) < 2:
        print("Available tasks:")
        for name, func in tasks.items():
            print(f"  {name:12} - {func.__doc__}")
        return

    task_name = sys.argv[1]
    if task_name not in tasks:
        print(f"Unknown task: {task_name}")
        sys.exit(1)

    tasks[task_name](sys.argv[2:])


if __name__ == "__main__":
    main()
