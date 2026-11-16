declare variable $db external;

let $entries := /*:replacement/*:document
return
    if (count($entries/*:competition) ne 1
        or (some $entry in $entries satisfies
            count($entry/*) ne 1
            or not(local-name($entry/*) = ('competition', 'application', 'evaluation', 'decision'))
            or string($entry/@path) ne concat(
                local-name($entry/*), 's/', string($entry/*/@id), '.xml'
            ))
        or count(distinct-values($entries/@path)) ne count($entries))
    then error(xs:QName('CM_RULE'))
    else (
        db:delete($db, ''),
        for $entry in $entries
        return db:put($db, $entry/*, string($entry/@path))
    )
