declare variable $db external;
declare variable $report external;
declare variable $competition external;
declare variable $threshold external;
declare variable $offset external;
declare variable $limit external;

declare function local:allocate(
    $rows as map(*)*,
    $remaining as xs:decimal
) as map(*)* {
    if (empty($rows)) then ()
    else
        let $row := head($rows)
        let $budgetAllows := xs:decimal($row?requestedAmount) le $remaining
        let $award := $row?qualified and $budgetAllows
        let $remainingAfter :=
            if ($award) then $remaining - xs:decimal($row?requestedAmount)
            else $remaining
        let $result := map:merge((
            $row,
            map {
                'budgetAllows': $budgetAllows,
                'outcome': if ($award) then 'AWARD' else 'REJECT',
                'remainingAfter': string($remainingAfter)
            }
        ), map { 'duplicates': 'use-last' })
        return ($result, local:allocate(tail($rows), $remainingAfter))
};

let $documents := db:get($db)/*
let $configuration := $documents[self::*:competition][@id = $competition]
let $applications := $documents[self::*:application][*:competitionId = $competition]
let $issuedDecisions := $documents[self::*:decision]
    [*:applicationId = $applications/@id][*:state = 'ISSUED']
let $evaluations := $documents[self::*:evaluation]
    [*:applicationId = $applications/@id][*:state = 'FINAL']
    [not(*:applicationId = $issuedDecisions/*:applicationId)]
let $remaining := xs:decimal($configuration/*:budget)
    - sum($issuedDecisions[*:outcome = 'AWARD']/*:amount ! xs:decimal(.))

let $ranking :=
    for $evaluation in $evaluations
    let $application := $applications[@id = $evaluation/*:applicationId]
    order by
        xs:integer($evaluation/*:total) descending,
        xs:dateTime($application/*:submittedAt),
        string($application/@id)
    return map {
        'applicationId': string($application/@id),
        'evaluationId': string($evaluation/@id),
        'title': string($application/*:title),
        'score': xs:integer($evaluation/*:total),
        'qualified': $evaluation/*:eligible = 'true'
            and xs:integer($evaluation/*:total) >= xs:integer($configuration/*:threshold),
        'requestedAmount': string($application/*:requestedAmount)
    }
let $rows := local:allocate($ranking, $remaining)

return
    if (empty($configuration)) then error(xs:QName('CM_NOT_FOUND'))
    else serialize(map {
        'total': count($rows),
        'rows': array { subsequence($rows, xs:integer($offset) + 1, xs:integer($limit)) }
    }, map { 'method': 'json' })
