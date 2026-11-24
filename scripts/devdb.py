"""Local PostgreSQL 16 for ClaimFlow without Docker or admin rights.

Uses the binaries shipped in the `pgserver` wheel. Data lives in .local/pgdata.

    uv run --with pgserver python scripts/devdb.py start
    uv run --with pgserver python scripts/devdb.py stop
"""

import argparse
import os
import subprocess
import sys
from pathlib import Path

DEFAULT_PORT = 54340
DATABASES = ("claimflow_members", "claimflow_claims")
ROOT = Path(__file__).resolve().parent.parent


def _bin(name: str) -> str:
    from pgserver._commands import POSTGRES_BIN_PATH

    suffix = ".exe" if sys.platform == "win32" else ""
    return str(POSTGRES_BIN_PATH / f"{name}{suffix}")


def _run(args: list[str], check: bool = True) -> int:
    # pg_ctl leaves the server holding inherited pipes, so never capture its output.
    return subprocess.run(args, check=check, stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL,
                          stderr=subprocess.DEVNULL).returncode


def is_running(pgdata: Path) -> bool:
    return _run([_bin("pg_ctl"), "-D", str(pgdata), "status"], check=False) == 0


def start(pgdata: Path, port: int) -> None:
    if not (pgdata / "PG_VERSION").exists():
        pgdata.mkdir(parents=True, exist_ok=True)
        _run([_bin("initdb"), "-D", str(pgdata), "-U", "postgres", "--auth=trust", "-E", "UTF8"])
    if not is_running(pgdata):
        _run([_bin("pg_ctl"), "-D", str(pgdata), "-l", str(pgdata / "server.log"),
              "-o", f"-h 127.0.0.1 -p {port}", "-w", "start"])
    psql = [_bin("psql"), "-h", "127.0.0.1", "-p", str(port), "-U", "postgres", "-q", "-tA"]
    for name in DATABASES:
        exists = subprocess.run([*psql, "-c", f"select 1 from pg_database where datname = '{name}'"],
                                check=True, capture_output=True, text=True, stdin=subprocess.DEVNULL).stdout.strip()
        if not exists:
            _run([*psql, "-c", f"create database {name}"])
    print(f"PostgreSQL running on 127.0.0.1:{port} with databases {', '.join(DATABASES)}")


def stop(pgdata: Path) -> None:
    if is_running(pgdata):
        _run([_bin("pg_ctl"), "-D", str(pgdata), "-m", "fast", "-w", "stop"])


def main() -> None:
    parser = argparse.ArgumentParser(prog="devdb")
    parser.add_argument("command", choices=["start", "stop", "status"])
    parser.add_argument("--pgdata", type=Path, default=ROOT / ".local" / "pgdata")
    parser.add_argument("--port", type=int, default=int(os.getenv("DEVDB_PORT", DEFAULT_PORT)))
    args = parser.parse_args()
    pgdata = args.pgdata.resolve()
    if args.command == "start":
        start(pgdata, args.port)
    elif args.command == "stop":
        stop(pgdata)
    else:
        print("running" if is_running(pgdata) else "stopped")


if __name__ == "__main__":
    main()
