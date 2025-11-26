Task: Identify Logical Responsibility Clusters

---

Goal:
Analyze the provided source code and structural metrics and determine how the class should be decomposed into smaller responsibility-based clusters.

Important:
- Do NOT output any code.
- Do NOT output explanations, rationale, or comments.
- Output ONLY the clusters of methods grouped by the conceptual classes you recommend.
- Every cluster MUST contain only method names exactly as they appear in the source code.
- Use the output format shown below exactly.
- You MUST base your clustering decisions ONLY on the provided metrics, dependencies, field usage, method call relationships, workflow entry points, and workflow membership.
- You MUST treat workflow entry points and workflow membership as primary signals when grouping methods.
- Methods that share workflow roots should be grouped together.
- Helper methods that belong to multiple workflows should be grouped according to the workflow they most strongly support.

---

Context and Analysis:

Class Name: ${className}

Metrics:
<#if nom?? && (nom?number > 0)>• NOM: ${nom}</#if>
<#if noc?? && (noc?number > 0)> • NOC: ${noc}</#if>
<#if cbo?? && (cbo?number > 0)> • CBO: ${cbo}</#if>
<#if lcom?? && (lcom?number > 0)> • LCOM: ${lcom}</#if>
<#if connectivity?? && (connectivity?number > 0)> • Conn: ${connectivity}</#if>

Dependencies:
Depends On:
<#if dependsOn?size gt 0>
→ <#list dependsOn as d>${d.class} [${d.type}]<#if d_has_next>, </#if></#list>
<#else>none</#if>

Depended By:
<#if dependedBy?size gt 0>
← <#list dependedBy as d>${d.class} [${d.type}]<#if d_has_next>, </#if></#list>
<#else>none</#if>

Fields:
<#if fields?size gt 0>
<#list fields as f>
• ${f.name} (read by: <#if f.readBy?size gt 0><#list f.readBy as r>${r}<#if r_has_next>,</#if></#list><#else>-</#if>, written by: <#if f.writtenBy?size gt 0><#list f.writtenBy as w>${w}<#if w_has_next>,</#if></#list><#else>-</#if>)
</#list>
<#else>No fields.</#if>

Methods:
<#if methods?size gt 0>
  <#list methods as m>
    • ${m.name}
      (calls: <#if m.calls?size gt 0><#list m.calls as c>${c}<#if c_has_next>, </#if></#list><#else>-</#if>,
       called by: <#if m.calledBy?size gt 0><#list m.calledBy as cb>${cb}<#if cb_has_next>, </#if></#list><#else>-</#if>)
  </#list>
<#else>
  No methods.
</#if>

---

Workflow Analysis:

Workflow Entry Points:
<#if workflowRoots?size gt 0>
• <#list workflowRoots as r>${r}<#if r_has_next>, </#if></#list>
<#else>
None
</#if>

Method → Workflow Roots:
<#if workflowMembership?size gt 0>
<#list workflowMembership as wm>
• ${wm.method}: <#if wm.roots?size gt 0>[<#list wm.roots as rt>${rt}<#if rt_has_next>, </#if></#list>]<#else>[]</#if>
</#list>
<#else>
None
</#if>

---

Original Source Code:
${sourceCode}

---

Output Format (strict):
Return a JSON object where each key is a new conceptual class name
and each value is a list of method names from the original class.

Example:
{
  "ClassA": ["method1", "method2"],
  "ClassB": ["method3"]
}

---

Produce only the JSON object and nothing else.
