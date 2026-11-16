declare variable $db external;

let $entries := /*:batch/*:entry
let $documents := db:get($db)/*
let $decisions :=
    for $entry in $entries
    return $documents[self::*:decision][@id = $entry/@id]
let $existingAwards := $documents[self::*:decision]
    [*:state = 'ISSUED'][*:outcome = 'AWARD']
let $competitionIds := distinct-values(
    for $decision in $decisions
    return $documents[self::*:application][@id = $decision/*:applicationId]/*:competitionId
)

let $invalid :=
    for $entry in $entries
    let $decision := $decisions[@id = $entry/@id]
    let $application := $documents[self::*:application][@id = $decision/*:applicationId]
    let $evaluation := $documents[self::*:evaluation][@id = $decision/*:evaluationId]
    let $configuration := $documents[self::*:competition][@id = $application/*:competitionId]
    where
        empty($decision)
        or $decision/*:state != 'DRAFT'
        or xs:integer($decision/@revision) != xs:integer($entry/@revision)
        or empty($application)
        or empty($evaluation)
        or $evaluation/*:state != 'FINAL'
        or $evaluation/*:applicationId != $application/@id
        or ($decision/*:outcome = 'AWARD' and (
            $evaluation/*:eligible != 'true'
            or xs:integer($evaluation/*:total) < xs:integer($configuration/*:threshold)
            or xs:decimal($decision/*:amount) != xs:decimal($application/*:requestedAmount)
        ))
        or ($decision/*:outcome = 'REJECT' and xs:decimal($decision/*:amount) != 0)
    return $entry

let $overBudget :=
    for $id in $competitionIds
    let $configuration := $documents[self::*:competition][@id = $id]
    let $applicationIds := $documents[self::*:application][*:competitionId = $id]/@id
    let $committedAmount := sum((
        $existingAwards[*:applicationId = $applicationIds]/*:amount,
        $decisions[*:applicationId = $applicationIds][*:outcome = 'AWARD']/*:amount
    ) ! xs:decimal(.))
    where $committedAmount > xs:decimal($configuration/*:budget)
    return $id

return
    if (empty($entries)
        or count(distinct-values($entries/@id)) != count($entries)
        or exists($invalid)
        or exists($overBudget))
    then error(xs:QName('CM_CONFLICT'))
    else
        for $decision in $decisions
        let $issued :=
            copy $copy := $decision
            modify (
                replace value of node $copy/*:state with 'ISSUED',
                replace value of node $copy/@revision with xs:integer($copy/@revision) + 1,
                replace value of node $copy/*:date with format-date(current-date(), '[Y0001]-[M01]-[D01]')
            )
            return $copy
        return db:put($db, $issued, concat('decisions/', $decision/@id, '.xml'))
