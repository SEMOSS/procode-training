export interface Engine {
    app_id: string;
    app_name: string;
    app_type: 'DATABASE' | 'MODEL' | string;
}
