export interface Engine {
	app_id: string;
	app_name: string;
}

export interface SemossFile {
	fileName: string;
	fileSize: number; // in KB
	lastModified: string; // "YYYY-MM-DD HH:mm:ss"
}
