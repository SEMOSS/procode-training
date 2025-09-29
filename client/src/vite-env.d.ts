interface ImportMetaEnv {
    readonly CLIENT_MODULE: string;
    readonly CLIENT_ACCESS_KEY: string;
    readonly CLIENT_SECRET_KEY: string;
    readonly CLIENT_APP: string;
    // more env variables...
}

interface ImportMeta {
    readonly env: ImportMetaEnv;
}
