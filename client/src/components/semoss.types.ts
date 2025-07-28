export interface Engine {
    app_id: string;
    app_name: string;
    app_type: 'DATABASE' | 'MODEL' | string;
}

export interface ReactorResponse {
    question: string;
    explanation: string;
    sql: string;
    result_set: ResultSet;
}

export interface ResultSet {
    rows: unknown[][];
    columns: ColumnDefinition[];
}

export interface ColumnDefinition {
    key: string;
    type: string;
}
