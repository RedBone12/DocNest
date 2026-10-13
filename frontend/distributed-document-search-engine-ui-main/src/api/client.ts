export async function apiFetch(path: string, options: RequestInit = {}) {
	const token = localStorage.getItem("authToken");
	const headers = new Headers(options.headers || {});
	
	if (token) {
		headers.set("Authorization", `Bearer ${token}`);
	}

	const res = await fetch(path, {
		credentials: "include",
		...options,
		headers,
	});

	const contentType = res.headers.get("content-type") || "";

	if (res.ok) {
		if (contentType.includes("application/json")) return res.json();
		return res.text();
	}

	const errText = await res.text();
	throw new Error(errText || res.statusText || "API error");
}
