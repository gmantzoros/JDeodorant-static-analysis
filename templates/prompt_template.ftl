=== REFACTORING TASK PROMPT ===

Task: Perform an **Extract Class Refactoring** on the provided source code.

Instructions:
- Do not include explanations or comments; output only the refactored code.
- Use the provided static analysis data as context.
- Ensure the refactoring improves cohesion, reduces class responsibilities, and respects existing architectural boundaries.
- Maintain naming consistency, method visibility, and dependency integrity.

---

Static Analysis Context:

Class: ${className}

[Metrics]
<#if nom??> - NOM (Number of Methods): ${nom}</#if>
<#if noc??> - NOC (Number of Children): ${noc}</#if>
<#if cbo??> - CBO (Coupling Between Objects): ${cbo}</#if>
<#if lcom??> - LCOM (Lack of Cohesion): ${lcom}</#if>
<#if connectivity??> - Connectivity: ${connectivity}</#if>
<#if fanIn??> - Fan-In (Dependents): ${fanIn}</#if>
<#if fanOut??> - Fan-Out (Dependencies): ${fanOut}</#if>

[Dependencies]
<#if dependsOn?size gt 0>
- Depends On:
  <#list dependsOn as d>  * ${d}</#list>
<#else>
- Depends On: none
</#if>

<#if dependedBy?size gt 0>
- Depended By:
  <#list dependedBy as d>  * ${d}</#list>
<#else>
- Depended By: none
</#if>

[Fields and Access Patterns]
<#if fields?size gt 0>
<#list fields as f>
- Field: ${f.name}
  <#if f.readBy?size gt 0>
  Read by:
    <#list f.readBy as r>    • ${r}</#list>
  <#else>
  Read by: none
  </#if>
  <#if f.writtenBy?size gt 0>
  Written by:
    <#list f.writtenBy as w>    • ${w}</#list>
  <#else>
  Written by: none
  </#if>
</#list>
<#else>
- No fields found.
</#if>

[Methods and Relationships]
<#if methods?size gt 0>
<#list methods as m>
- Method: ${m.name}
  <#if m.calls?size gt 0>
  Calls:
    <#list m.calls as c>    • ${c}</#list>
  <#else>
  Calls: none
  </#if>
  <#if m.calledBy?size gt 0>
  Called By:
    <#list m.calledBy as cb>    • ${cb}</#list>
  <#else>
  Called By: none
  </#if>
</#list>
<#else>
- No methods found.
</#if>

---

Source Code:
${sourceCode}