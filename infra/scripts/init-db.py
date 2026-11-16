#!/usr/bin/env python3
"""Create the local database and its restricted application account."""

from pathlib import Path
import base64
import secrets
import re
import urllib.request
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]


def env(path):
    return dict(
        line.split("=", 1)
        for line in path.read_text().splitlines()
        if line and not line.startswith("#")
    )


admin = env(ROOT / "infra/.env")["BASEX_PASSWORD"]
config = ROOT / "backend/.env"
if not config.exists():
    config.write_text(
        "BASEX_URL=http://127.0.0.1:8984/rest\nBASEX_USERNAME=cloudmigration\nBASEX_PASSWORD="
        + secrets.token_urlsafe(32)
        + "\nBASEX_DATABASE=cloudmigration\n"
    )
    config.chmod(0o600)
settings = env(config)
if settings["BASEX_USERNAME"] == "admin" or not re.fullmatch(
    r"[A-Za-z][A-Za-z0-9_-]*", settings["BASEX_USERNAME"]
):
    raise SystemExit("Use a dedicated application username, not admin.")
if not re.fullmatch(r"[A-Za-z][A-Za-z0-9_-]*", settings["BASEX_DATABASE"]):
    raise SystemExit("Invalid database name.")


def query(source, variables):
    root = ET.Element("query", xmlns="http://basex.org/rest")
    ET.SubElement(root, "text").text = source
    for name, value in variables.items():
        ET.SubElement(root, "variable", name=name, value=value)
    request = urllib.request.Request(
        settings["BASEX_URL"],
        data=ET.tostring(root),
        headers={
            "Authorization": "Basic "
            + base64.b64encode(("admin:" + admin).encode()).decode(),
            "Content-Type": "application/xml",
        },
    )
    with urllib.request.urlopen(request, timeout=15) as response:
        response.read()


query(
    "declare variable $db external; if(db:exists($db)) then () else db:create($db)",
    {"db": settings["BASEX_DATABASE"]},
)
query(
    'declare variable $db external; let $test := concat($db,"_tests") return if(db:exists($test)) then () else db:create($test)',
    {"db": settings["BASEX_DATABASE"]},
)
query(
    "declare variable $db external; declare variable $user external; declare variable $password external; if(user:exists($user)) then user:password($user,$password) else user:create($user,$password)",
    {
        "db": settings["BASEX_DATABASE"],
        "user": settings["BASEX_USERNAME"],
        "password": settings["BASEX_PASSWORD"],
    },
)
query(
    "declare variable $db external; declare variable $user external; user:grant($user,'none')",
    {"db": settings["BASEX_DATABASE"], "user": settings["BASEX_USERNAME"]},
)
query(
    "declare variable $db external; declare variable $user external; user:grant($user,('write','write'),($db,concat($db,'_tests')))",
    {"db": settings["BASEX_DATABASE"], "user": settings["BASEX_USERNAME"]},
)
print(
    "Database and restricted application account ready. Configuration: backend/.env"
)
