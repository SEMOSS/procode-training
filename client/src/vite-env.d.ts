interface ImportMetaEnv {
	readonly ENDPOINT: string;
	readonly MODULE: string;
	readonly CLIENT_ACCESS_KEY: string;
	readonly CLIENT_SECRET_KEY: string;
	readonly CLIENT_APP: string;
	readonly CLIENT_EMBEDDER_ENGINE: string;
	// more env variables...
}

interface ImportMeta {
	readonly env: ImportMetaEnv;
}
