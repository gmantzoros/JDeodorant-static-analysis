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
<#if nom?? || noc?? || cbo?? || lcom?? || connectivity??>
Metrics:
<#if nom??>• NOM: ${nom}</#if>
<#if noc??> • NOC: ${noc}</#if>
<#if cbo??> • CBO: ${cbo}</#if>
<#if lcom??> • LCOM: ${lcom}</#if>
<#if connectivity??> • Conn: ${connectivity}</#if>
</#if>

<#-- Dependencies -->
Depends On:
<#if dependsOn?size gt 0>
→ <#list dependsOn as d>${d.class} [${d.type}]<#if d_has_next>, </#if></#list>
<#else>none</#if>

Depended By:
<#if dependedBy?size gt 0>
← <#list dependedBy as d>${d.class} [${d.type}]<#if d_has_next>, </#if></#list>
<#else>none</#if>

<#-- Fields -->
<#if fields?size gt 0>
Fields:
<#list fields as f>
• ${f.name} | <#if f.readBy?size gt 0><#list f.readBy as r>${r}<#if r_has_next>,</#if></#list><#else>-</#if> | <#if f.writtenBy?size gt 0><#list f.writtenBy as w>${w}<#if w_has_next>,</#if></#list><#else>-</#if>
</#list>
<#else>No fields.</#if>

<#-- Methods -->
<#if methods?size gt 0>
Methods:
<#list methods as m>
• ${m.name} | Calls:<#if m.calls?size gt 0><#list m.calls as c>${c}<#if c_has_next>,</#if></#list><#else>-</#if> | Called By:<#if m.calledBy?size gt 0><#list m.calledBy as cb>${cb}<#if cb_has_next>,</#if></#list><#else>-</#if>
</#list>
<#else>No methods.</#if>

---

Original Source Code:
${sourceCode}