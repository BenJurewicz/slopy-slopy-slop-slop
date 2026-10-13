declare variable $db external;
declare variable $report external;
declare variable $competition external;
declare variable $threshold external;
declare variable $offset external;
declare variable $limit external;
let $all := db:get($db)/*
let $c := $all[self::*:competition][@id=$competition]
let $apps := $all[self::*:application][*:competitionId=$competition]
let $evals := $all[self::*:evaluation][*:applicationId=$apps/@id][*:state='FINAL']
let $decisions := $all[self::*:decision][*:applicationId=$apps/@id][*:state='ISSUED']
let $rows :=
 if($report='summary') then (map{'applications':count($apps),'evaluations':count($evals),'averageScore':if(exists($evals)) then avg($evals/*:total ! xs:integer(.)) else 0,'awarded':string(sum($decisions[*:outcome='AWARD']/*:amount ! xs:decimal(.))),'remaining':string(xs:decimal($c/*:budget)-sum($decisions[*:outcome='AWARD']/*:amount ! xs:decimal(.)))})
 else if($report='decisions') then for $d in $decisions let $a := $apps[@id=$d/*:applicationId] return map{'id':string($d/@id),'applicationId':string($a/@id),'title':string($a/*:title),'company':string($a/*:company/*:name),'outcome':string($d/*:outcome),'amount':string($d/*:amount)}
 else if($report='providers') then for $provider in distinct-values($apps/*:targetInfrastructure/*:provider) return map{'provider':$provider,'projects':count($apps[*:targetInfrastructure/*:provider=$provider])}
 else if($report=('ranking','qualified')) then
 for $e in $evals let $a := $apps[@id=$e/*:applicationId]
 let $qualified := $e/*:eligible='true' and xs:integer($e/*:total)>=xs:integer($c/*:threshold)
 where $report='ranking' or $qualified
 order by xs:integer($e/*:total) descending,xs:dateTime($a/*:submittedAt),string($a/@id)
 return map{'applicationId':string($a/@id),'evaluationId':string($e/@id),'title':string($a/*:title),'company':string($a/*:company/*:name),'score':xs:integer($e/*:total),'qualified':$qualified,'requestedAmount':string($a/*:requestedAmount)}
 else for $a in $apps
 where ($report='applications') or ($report='over-budget' and xs:decimal($a/*:requestedAmount)>xs:decimal($threshold)) or ($report='missing-evaluations' and $a/*:state='SUBMITTED' and empty($evals[*:applicationId=$a/@id]))
 order by string($a/*:title)
 return map{'applicationId':string($a/@id),'title':string($a/*:title),'company':string($a/*:company/*:name),'state':string($a/*:state),'requestedAmount':string($a/*:requestedAmount)}
return if(empty($c)) then error(xs:QName('CM_NOT_FOUND')) else serialize(map{'total':count($rows),'rows':array{subsequence($rows,xs:integer($offset)+1,xs:integer($limit))}},map{'method':'json'})
