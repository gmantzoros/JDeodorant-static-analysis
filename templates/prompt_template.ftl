=== CLASS CONTEXT PROMPT ===
Class: ${className}

[Metrics]
NOM: ${nom}
NOC: ${noc}
CBO: ${cbo}
LCOM: ${lcom}
Connectivity: ${connectivity}
Fan-In: ${fanIn}
Fan-Out: ${fanOut}

[Dependencies]
Depends On:
<#list dependsOn as d> - ${d}
</#list>
Depended By:
<#list dependedBy as d> - ${d}
</#list>

[Fields]
<#list fields as f>
- ${f.name}
  Read by:
  <#list f.readBy as r>   * ${r}</#list>
  Written by:
  <#list f.writtenBy as w>   * ${w}</#list>
</#list>

[Methods]
<#list methods as m>
- ${m.name}
  Calls:
  <#list m.calls as c>   * ${c}</#list>
  Called By:
  <#list m.calledBy as cb>   * ${cb}</#list>
</#list>

[Source Code]
${sourceCode}