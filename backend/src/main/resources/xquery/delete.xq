declare variable $db external;
declare variable $path external;
declare variable $expected external;

let $document :=
    if (db:exists($db, $path)) then db:get($db, $path)/*
    else error(xs:QName('CM_NOT_FOUND'))
let $id := string($document/@id)
let $references := db:get($db)/*[
    *:competitionId = $id or *:applicationId = $id or *:evaluationId = $id
]

return
    if ($document/self::*:competition
        or xs:integer($document/@revision) != xs:integer($expected)
        or exists($references)
        or $document/*:state = ('SUBMITTED', 'FINAL', 'ISSUED'))
    then error(xs:QName('CM_CONFLICT'))
    else db:delete($db, $path)
