#!/usr/bin/env python3
"""Enrich fictional v1 demo records in place, atomically, WITHOUT making a backup."""

import argparse, base64, copy, os, shutil, subprocess, urllib.request
from collections import Counter
from datetime import datetime
from decimal import Decimal
from pathlib import Path
import xml.etree.ElementTree as E

ROOT = Path(__file__).resolve().parents[2]


def env(path):
    return dict(
        line.split("=", 1)
        for line in path.read_text().splitlines()
        if line and not line.startswith("#")
    )


def local(element):
    return element.tag.split("}")[-1]


def field(element, name):
    return next((child for child in element if local(child) == name), None)


def text(element, name):
    found = field(element, name)
    return "" if found is None else found.text or ""


def add(element, name, value, namespace=None):
    if field(element, name) is None:
        namespace = namespace or element.tag[1:].split("}")[0]
        E.SubElement(element, "{" + namespace + "}" + name).text = str(value)


def preserve(before, after):
    """All original element values, order and attributes must survive enrichment."""
    assert before.tag.replace(":v1}", ":v2}") == after.tag
    assert (
        before.attrib == after.attrib
        and (before.text or "").strip() == (after.text or "").strip()
    )
    assert len(after) >= len(before)
    for old, new in zip(before, after):
        preserve(old, new)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--apply",
        action="store_true",
        help="Apply after XML validation; default is a read-only check.",
    )
    args = parser.parse_args()
    settings = env(ROOT / "backend/.env")
    password = env(ROOT / "infra/.env")["BASEX_PASSWORD"]
    database = settings["BASEX_DATABASE"]
    authorization = (
        "Basic " + base64.b64encode(("admin:" + password).encode()).decode()
    )

    def query(source, context=None, variables=()):
        request = E.Element("query", xmlns="http://basex.org/rest")
        E.SubElement(request, "text").text = source
        E.SubElement(request, "variable", name="db", value=database)
        for name, value in variables:
            E.SubElement(request, "variable", name=name, value=str(value))
        if context is not None:
            E.SubElement(request, "context").append(context)
        call = urllib.request.Request(
            settings["BASEX_URL"],
            data=E.tostring(request),
            headers={
                "Content-Type": "application/xml",
                "Authorization": authorization,
            },
        )
        with urllib.request.urlopen(call, timeout=45) as response:
            return response.read()

    source = 'declare variable $db external; <documents>{for $d in db:get($db)/* return <stored path="{db:path($d)}">{$d}</stored>}</documents>'
    original = E.fromstring(query(source))
    payload = E.Element("migration")
    records = {entry[0].get("id"): entry[0] for entry in original}
    # Reconstruct the original full-grant ranking to record each proposal's budget snapshot.
    budget_flags = {}
    for competition in (
        d for d in records.values() if local(d) == "competition"
    ):
        remaining = Decimal(text(competition, "budget"))
        evaluations = [
            d
            for d in records.values()
            if local(d) == "evaluation"
            and text(d, "state") == "FINAL"
            and text(records[text(d, "applicationId")], "competitionId")
            == competition.get("id")
        ]
        evaluations.sort(
            key=lambda e: (
                -int(text(e, "total")),
                datetime.fromisoformat(
                    text(
                        records[text(e, "applicationId")], "submittedAt"
                    ).replace("Z", "+00:00")
                ),
                text(e, "applicationId"),
            )
        )
        for evaluation in evaluations:
            requested = Decimal(
                text(
                    records[text(evaluation, "applicationId")],
                    "requestedAmount",
                )
            )
            allows = requested <= remaining
            budget_flags[evaluation.get("id")] = allows
            if (
                allows
                and text(evaluation, "eligible") == "true"
                and int(text(evaluation, "total"))
                >= int(text(competition, "threshold"))
            ):
                remaining -= requested
    for stored in original:
        old = stored[0]
        if ":v1}" not in old.tag:
            continue
        document = copy.deepcopy(old)
        for node in document.iter():
            node.tag = node.tag.replace(":v1}", ":v2}")
        kind, identity = local(document), document.get("id")
        year = text(document, "date")[:4] or "2026"
        number = lambda prefix: f"{prefix}/{year}/{identity[:8].upper()}"
        if kind == "application":
            company = field(document, "company")
            index = int(identity[:8], 16) % 999
            add(
                company,
                "address",
                f"ul. Przykładowa {index + 1}, 80-001 Gdańsk",
                "urn:cloudmigration:common:v2",
            )
            add(
                company,
                "regon",
                f"{100000000 + index:09d}",
                "urn:cloudmigration:common:v2",
            )
            add(
                company,
                "representative",
                text(company, "contact")
                + " - osoba upoważniona do reprezentacji",
                "urn:cloudmigration:common:v2",
            )
            tasks = [task for task in document if local(task) == "tasks"]
            start, end = (
                min(text(t, "start") for t in tasks),
                max(text(t, "end") for t in tasks),
            )
            cost = sum(
                (
                    Decimal(text(b, "total"))
                    for b in document
                    if local(b) == "budget"
                ),
                Decimal("0.00"),
            )
            own = cost - Decimal(text(document, "requestedAmount"))
            assert own >= 0, "Requested grant exceeds existing project budget"
            add(document, "number", number("WN"))
            add(document, "projectNumber", number("PR"))
            add(document, "implementationStart", start)
            add(document, "implementationEnd", end)
            add(document, "projectCost", f"{cost:.2f}")
            add(document, "ownContribution", f"{own:.2f}")
        elif kind == "evaluation":
            add(document, "number", number("OC"))
        elif kind == "decision":
            add(document, "number", number("DEC"))
            allows = text(document, "outcome") == "AWARD" or budget_flags.get(
                text(document, "evaluationId"), False
            )
            add(document, "budgetAllows", str(allows).lower())
        preserve(old, document)
        entry = E.SubElement(
            payload,
            "document",
            path=stored.get("path"),
            revision=old.get("revision"),
            namespace=old.tag[1:].split("}")[0],
        )
        entry.append(document)
    if not len(payload):
        print(
            "No v1 documents to enrich. Database unchanged; no backup created."
        )
        return
    java = (
        str(Path(os.environ["JAVA_HOME"]) / "bin/java")
        if "JAVA_HOME" in os.environ
        else shutil.which("java")
    )
    subprocess.run(
        [
            java,
            str(Path(__file__).with_name("ValidateXml.java")),
            str(ROOT / "backend/src/main/resources/schemas"),
        ],
        input=E.tostring(payload),
        check=True,
    )
    print(
        f"Prepared {len(payload)} records: {dict(Counter(local(e[0]) for e in payload))}. Existing fields and revisions preserved."
    )
    if not args.apply:
        print("Read-only check; use --apply to enrich without a backup.")
        return
    query(
        """declare variable $db external; declare variable $count external;
let $entries := /*:migration/*:document
return if(count(db:get($db)/*) ne xs:integer($count) or
  (some $e in $entries satisfies not(count(db:get($db,string($e/@path))/*)=1 and
   string(db:get($db,string($e/@path))/*/@revision)=string($e/@revision) and
   namespace-uri(db:get($db,string($e/@path))/*)=string($e/@namespace))))
then error(xs:QName('CM_CONFLICT'),'Database changed; rerun enrichment')
else for $e in $entries return db:put($db,$e/*,string($e/@path))""",
        payload,
        [("count", len(original))],
    )
    current = E.fromstring(query(source))
    assert len(current) == len(original)
    updated = {entry.get("path"): entry[0] for entry in current}
    for entry in original:
        preserve(entry[0], updated[entry.get("path")])
    print(
        f"Enriched {len(payload)} records atomically. IDs, links, amounts, scores, states and revisions unchanged. No backup created."
    )


if __name__ == "__main__":
    main()
