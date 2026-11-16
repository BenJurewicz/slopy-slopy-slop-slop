declare variable $db external;
declare variable $report external;
declare variable $competition external;
declare variable $threshold external;
declare variable $offset external;
declare variable $limit external;

declare function local:summary($applications, $evaluations, $decisions, $configuration) {
    let $awarded := sum($decisions[*:outcome = 'AWARD']/*:amount ! xs:decimal(.))
    return map {
        'applications': count($applications),
        'evaluations': count($evaluations),
        'averageScore':
            if (exists($evaluations)) then avg($evaluations/*:total ! xs:integer(.))
            else 0,
        'awarded': string($awarded),
        'remaining': string(xs:decimal($configuration/*:budget) - $awarded)
    }
};

declare function local:decisions($decisions, $applications) {
    for $decision in $decisions
    let $application := $applications[@id = $decision/*:applicationId]
    return map {
        'id': string($decision/@id),
        'applicationId': string($application/@id),
        'title': string($application/*:title),
        'company': string($application/*:company/*:name),
        'outcome': string($decision/*:outcome),
        'amount': string($decision/*:amount)
    }
};

declare function local:providers($applications) {
    for $provider in distinct-values($applications/*:targetInfrastructure/*:provider)
    return map {
        'provider': $provider,
        'projects': count($applications[*:targetInfrastructure/*:provider = $provider])
    }
};

declare function local:ranking($report, $applications, $evaluations, $configuration) {
    for $evaluation in $evaluations
    let $application := $applications[@id = $evaluation/*:applicationId]
    let $qualified := $evaluation/*:eligible = 'true'
        and xs:integer($evaluation/*:total) >= xs:integer($configuration/*:threshold)
    where $report = 'ranking' or $qualified
    order by
        xs:integer($evaluation/*:total) descending,
        xs:dateTime($application/*:submittedAt),
        string($application/@id)
    return map {
        'applicationId': string($application/@id),
        'evaluationId': string($evaluation/@id),
        'title': string($application/*:title),
        'company': string($application/*:company/*:name),
        'score': xs:integer($evaluation/*:total),
        'qualified': $qualified,
        'requestedAmount': string($application/*:requestedAmount)
    }
};

declare function local:applications($report, $applications, $evaluations, $threshold) {
    for $application in $applications
    where
        $report = 'applications'
        or ($report = 'over-budget'
            and xs:decimal($application/*:requestedAmount) > xs:decimal($threshold))
        or ($report = 'missing-evaluations'
            and $application/*:state = 'SUBMITTED'
            and empty($evaluations[*:applicationId = $application/@id]))
    order by string($application/*:title)
    return map {
        'applicationId': string($application/@id),
        'title': string($application/*:title),
        'company': string($application/*:company/*:name),
        'state': string($application/*:state),
        'requestedAmount': string($application/*:requestedAmount)
    }
};

let $documents := db:get($db)/*
let $configuration := $documents[self::*:competition][@id = $competition]
let $applications := $documents[self::*:application][*:competitionId = $competition]
let $evaluations := $documents[self::*:evaluation]
    [*:applicationId = $applications/@id][*:state = 'FINAL']
let $decisions := $documents[self::*:decision]
    [*:applicationId = $applications/@id][*:state = 'ISSUED']

let $rows :=
    switch ($report)
        case 'summary' return local:summary($applications, $evaluations, $decisions, $configuration)
        case 'decisions' return local:decisions($decisions, $applications)
        case 'providers' return local:providers($applications)
        case 'ranking'
        case 'qualified' return local:ranking($report, $applications, $evaluations, $configuration)
        default return local:applications($report, $applications, $evaluations, $threshold)

return
    if (empty($configuration)) then error(xs:QName('CM_NOT_FOUND'))
    else serialize(map {
        'total': count($rows),
        'rows': array { subsequence($rows, xs:integer($offset) + 1, xs:integer($limit)) }
    }, map { 'method': 'json' })
