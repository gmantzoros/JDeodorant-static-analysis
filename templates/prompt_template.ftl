Task: Perform an Extract Class Refactoring on the provided source code.

Instructions:
- Output only the refactored code — no explanations, comments, or rationale.
- Use the static analysis data below to guide your design decisions.
- Focus on improving cohesion, reducing class responsibilities, and maintaining architectural boundaries.
- Preserve original behavior, method visibility, and naming consistency.

---

Static Analysis Context:

The class ${className} has been analyzed to reveal its structure, dependencies, and internal relationships.

<#-- Metrics -->
<#if nom?? || noc?? || cbo?? || lcom?? || connectivity?? || fanIn?? || fanOut??>
It exhibits the following metrics:
<#if nom??>  • Number of Methods (NOM): ${nom}</#if>
<#if noc??>  • Number of Children (NOC): ${noc}</#if>
<#if cbo??>  • Coupling Between Objects (CBO): ${cbo}</#if>
<#if lcom??>  • Lack of Cohesion in Methods (LCOM): ${lcom}</#if>
<#if connectivity??>  • Connectivity: ${connectivity}</#if>
<#if fanIn??>  • Fan-In (Dependents): ${fanIn}</#if>
<#if fanOut??>  • Fan-Out (Dependencies): ${fanOut}</#if>
</#if>

<#-- Dependencies -->
<#if dependsOn?size gt 0 || dependedBy?size gt 0>
In terms of external coupling:
<#if dependsOn?size gt 0>
  - It depends on the following classes:
    <#list dependsOn as d>    • ${d}</#list>
<#else>
  - It has no outgoing dependencies.
</#if>
<#if dependedBy?size gt 0>
  - It is used by these classes:
    <#list dependedBy as d>    • ${d}</#list>
<#else>
  - It has no incoming dependents.
</#if>
</#if>

<#-- Fields -->
<#if fields?size gt 0>
Its fields and their access patterns are as follows:
<#list fields as f>
  • ${f.name}
    <#if f.readBy?size gt 0>Read by: <#list f.readBy as r>${r}<#if r_has_next>, </#if></#list><#else>Read by: none</#if>;
    <#if f.writtenBy?size gt 0>Written by: <#list f.writtenBy as w>${w}<#if w_has_next>, </#if></#list><#else>Written by: none</#if>.
</#list>
<#else>
No fields were detected in this class.
</#if>

<#-- Methods -->
<#if methods?size gt 0>
The methods and their interactions are as follows:
<#list methods as m>
  • ${m.name}
    <#if m.calls?size gt 0>Calls: <#list m.calls as c>${c}<#if c_has_next>, </#if></#list><#else>Calls: none</#if>;
    <#if m.calledBy?size gt 0>Called by: <#list m.calledBy as cb>${cb}<#if cb_has_next>, </#if></#list><#else>Called by: none</#if>.
</#list>
<#else>
No methods were found for this class.
</#if>

---

Source Code:
${sourceCode}
