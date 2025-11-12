Task: Review and Refactor the Provided Source Code

---

As a software developer, your were asked to review a class
and determine if it would benefit from Extract Class Refactoring or other modularization improvements.

Goal:
- Analyze the provided source code and static analysis data.
- If refactoring is beneficial, output only the refactored code — no explanations, comments, or rationale.
- Preserve original behavior, method visibility, and naming consistency.
- If no refactoring is needed, simply state: "No refactoring needed."

---

Context and Analysis

The class ${className} has been analyzed to reveal its structure, dependencies, and internal relationships.

<#-- Metrics -->
<#if nom?? || noc?? || cbo?? || lcom?? || connectivity?? || fanIn?? || fanOut??>
Metrics Overview:
<#if nom??>  • Number of Methods (NOM): ${nom}</#if>
<#if noc??>  • Number of Children (NOC): ${noc}</#if>
<#if cbo??>  • Coupling Between Objects (CBO): ${cbo}</#if>
<#if lcom??>  • Lack of Cohesion in Methods (LCOM): ${lcom}</#if>
<#if connectivity??>  • Connectivity: ${connectivity}</#if>
<#if fanIn??>  • Fan-In (Dependents): ${fanIn}</#if>
<#if fanOut??>  • Fan-Out (Dependencies): ${fanOut}</#if>
</#if>

<#-- Dependencies -->
Depends On:
<#list dependsOn as dep>
  → ${dep.class} (${dep.type})
</#list>

Depended By:
<#list dependedBy as dep>
  ← ${dep.class} (${dep.type})
</#list>

<#-- Fields -->
<#if fields?size gt 0>
Fields and Access Patterns:
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
Methods and Interactions:
<#list methods as m>
  • ${m.name}
    <#if m.calls?size gt 0>Calls: <#list m.calls as c>${c}<#if c_has_next>, </#if></#list><#else>Calls: none</#if>;
    <#if m.calledBy?size gt 0>Called by: <#list m.calledBy as cb>${cb}<#if cb_has_next>, </#if></#list><#else>Called by: none</#if>.
</#list>
<#else>
No methods were found for this class.
</#if>

---

Original Source Code:
${sourceCode}