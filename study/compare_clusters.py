import json
import sys
import numpy as np
import itertools

##############################################
#  GRI / AGRI IMPLEMENTATION (as provided)
##############################################

def grand_index(u: np.ndarray, v: np.ndarray, adjusted: bool=False) -> float:
    u = u.astype(np.double, copy=True)
    v = v.astype(np.double, copy=True)    
    
    _validate_parameters(u, v)
    
    ju, su, tu = _calculate_information_arrays(u)
    jv, sv, tv = _calculate_information_arrays(v)
    tmax = max(np.sum(tu), np.sum(tv))

    # Avoid division by zero in singletons
    if tmax == 0:
        return 0.0

    gri = _calculate_gri(ju, su, jv, sv, tmax)

    if not adjusted:
        return gri

    gri_expectation = _calculate_gri_expectation(ju, su, jv, sv, tmax)
    return (gri - gri_expectation) / (1.0 - gri_expectation) if gri_expectation != 1 else 0.0


def _calculate_information_arrays(u):
    k, n = u.shape
    i = np.triu_indices(n, 1)
    ju = np.dot(u.T, u)[i]
    su = np.dot(u.T, np.dot(np.ones((k, k)) - np.identity(k), u))[i]
    return ju, su, ju + su

def _calculate_gri(ju, su, jv, sv, tmax):
    a = np.sum(np.minimum(ju, jv))
    d = np.sum(np.minimum(su, sv))
    return (a + d) / tmax

def _calculate_gri_expectation(ju, su, jv, sv, tmax):
    a_expectation = _calculate_expectation(ju, jv)
    d_expectation = _calculate_expectation(su, sv)
    return (a_expectation + d_expectation) / tmax

def _calculate_expectation(ju, jv):
    m = len(ju)

    # Handle singleton clusters gracefully
    if m == 0:
        return 0.0

    x, y = np.sort(ju), np.sort(jv)
    expectation = 0.0

    j = m - 1
    for i in reversed(range(m)):
        while j >= 0 and x[i] <= y[j]:
            j -= 1        
        expectation += (m - j - 1) * x[i]
    
    i = m - 1
    for j in reversed(range(m)):
        while i >= 0 and x[i] > y[j]:
            i -= 1
        expectation += (m - i - 1) * y[j]
    
    return expectation / m

def _validate_parameters(u, v):
    if u.shape[1] != v.shape[1]:
        raise ValueError("arrays 'u' and 'v' must have the same number of columns")
    
    if np.any(u < 0.0) or np.any(u > 1.0) or np.any(v < 0.0) or np.any(v > 1.0):
        raise ValueError("all elements must assume values between 0.0 and 1.0")

##############################################
#  JSON LOADING
##############################################

def load_jd_clusters(path):
    with open(path, "r") as f:
        data = json.load(f)
    return [set(c["methods"]) for c in data["candidates"]]

def load_llm_clusters(path):
    with open(path, "r") as f:
        data = json.load(f)
    return [set(v) for v in data.values()]

##############################################
#  MEMBERSHIP MATRIX CREATION
##############################################

def clusters_to_membership_matrix(clusters, method_index):
    k = len(clusters)
    n = len(method_index)

    M = np.zeros((k, n), dtype=np.float64)

    for r, cluster in enumerate(clusters):
        for method in cluster:
            if method in method_index:
                M[r, method_index[method]] = 1.0

    return M

##############################################
#  BCubed-F1 SIMILARITY
##############################################

def bcubed_f1(jd_clusters, llm_clusters, all_methods):
    """
    Compute BCubed-F1 cluster similarity.
    Handles singleton clusters naturally.
    """
    # Build lookup tables: method → cluster
    jd_map = {}
    llm_map = {}

    for c in jd_clusters:
        for m in c:
            jd_map[m] = c

    for c in llm_clusters:
        for m in c:
            llm_map[m] = c

    precisions = []
    recalls = []

    for m in all_methods:
        jd_c = jd_map.get(m, set())
        llm_c = llm_map.get(m, set())

        if not jd_c and not llm_c:
            continue  # method appears nowhere

        intersection = len(jd_c & llm_c)

        # Avoid div by zero
        p = intersection / len(llm_c) if llm_c else 0.0
        r = intersection / len(jd_c) if jd_c else 0.0

        precisions.append(p)
        recalls.append(r)

    if not precisions:
        return 0.0

    P = sum(precisions) / len(precisions)
    R = sum(recalls) / len(recalls)

    if P + R == 0:
        return 0.0

    return 2 * P * R / (P + R)

##############################################
#  MAIN COMPARISON
##############################################

def compare(jd_path, llm_no_path, llm_ctx_path):

    # Load clusters
    jd = load_jd_clusters(jd_path)
    llm_no = load_llm_clusters(llm_no_path)
    llm_ctx = load_llm_clusters(llm_ctx_path)

    # Compute full method universe
    all_methods = sorted(set().union(*jd, *llm_no, *llm_ctx))
    method_index = {m: i for i, m in enumerate(all_methods)}

    # Convert to membership matrices
    U_jd = clusters_to_membership_matrix(jd, method_index)
    U_no = clusters_to_membership_matrix(llm_no, method_index)
    U_ctx = clusters_to_membership_matrix(llm_ctx, method_index)

    # Compute GRIs
    gri_no = grand_index(U_jd, U_no, adjusted=False)
    gri_ctx = grand_index(U_jd, U_ctx, adjusted=False)

    # Compute AGRIs
    agri_no = grand_index(U_jd, U_no, adjusted=True)
    agri_ctx = grand_index(U_jd, U_ctx, adjusted=True)

    # Compute BCubed-F1
    bc_no = bcubed_f1(jd, llm_no, all_methods)
    bc_ctx = bcubed_f1(jd, llm_ctx, all_methods)

    # Print results
    print("\n===============================")
    print("      GRI / AGRI / BCubed-F1")
    print("===============================\n")

    print("Comparison vs JDeodorant:")
    print("---------------------------------")
    
    print(f"GRI  (Context):      {gri_ctx:.4f}")
    print(f"AGRI (Context):      {agri_ctx:.4f}")
    print(f"BCubed-F1 (Context): {bc_ctx:.4f}")
    print()
    print(f"GRI  (No Context):   {gri_no:.4f}")
    print(f"AGRI (No Context):   {agri_no:.4f}")
    print(f"BCubed-F1 (No Ctx):  {bc_no:.4f}")
    
    print("---------------------------------\n")

    print("Metric meanings:")
    print("• GRI:  pairwise structural similarity (ignores singletons).")
    print("• AGRI: chance-adjusted GRI.")
    print("• BCubed-F1: cluster-level similarity including singletons.")

##############################################
# CLI
##############################################

if __name__ == "__main__":
    if len(sys.argv) != 4:
        print("\nUsage:")
        print("  python3 cluster_compare.py <jdeodorant.json> <llm_no_context.json> <llm_context.json>\n")
        sys.exit(1)

    compare(sys.argv[1], sys.argv[2], sys.argv[3])
