declare variable $db external;
let $entries := /*:batch/*:entry
let $all := db:get($db)/*
let $decisions := for $entry in $entries return $all[self::*:decision][@id=$entry/@id]
let $existingAwards := $all[self::*:decision][*:state='ISSUED'][*:outcome='AWARD']
let $competitionIds := distinct-values(for $d in $decisions return $all[self::*:application][@id=$d/*:applicationId]/*:competitionId)
let $invalid := for $entry in $entries
 let $d := $decisions[@id=$entry/@id]
 let $a := $all[self::*:application][@id=$d/*:applicationId]
 let $e := $all[self::*:evaluation][@id=$d/*:evaluationId]
 let $c := $all[self::*:competition][@id=$a/*:competitionId]
 where empty($d) or $d/*:state!='DRAFT' or xs:integer($d/@revision)!=xs:integer($entry/@revision)
 or empty($a) or empty($e) or $e/*:state!='FINAL' or $e/*:applicationId!=$a/@id
 or ($d/*:outcome='AWARD' and ($e/*:eligible!='true' or xs:integer($e/*:total)<xs:integer($c/*:threshold) or xs:decimal($d/*:amount)!=xs:decimal($a/*:requestedAmount)))
 or ($d/*:outcome='REJECT' and xs:decimal($d/*:amount)!=0)
 return $entry
let $overBudget := for $id in $competitionIds
 let $c := $all[self::*:competition][@id=$id]
 let $appIds := $all[self::*:application][*:competitionId=$id]/@id
 let $sum := sum(($existingAwards[*:applicationId=$appIds]/*:amount,$decisions[*:applicationId=$appIds][*:outcome='AWARD']/*:amount) ! xs:decimal(.))
 where $sum>xs:decimal($c/*:budget)
 return $id
return if(empty($entries) or count(distinct-values($entries/@id))!=count($entries) or exists($invalid) or exists($overBudget)) then error(xs:QName('CM_CONFLICT'))
else for $d in $decisions
 let $issued := copy $copy := $d modify (
 replace value of node $copy/*:state with 'ISSUED',
 replace value of node $copy/@revision with xs:integer($copy/@revision)+1,
 replace value of node $copy/*:date with format-date(current-date(),'[Y0001]-[M01]-[D01]')
 ) return $copy
 return db:put($db,$issued,concat('decisions/',$d/@id,'.xml'))
