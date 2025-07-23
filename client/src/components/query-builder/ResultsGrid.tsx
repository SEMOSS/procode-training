import { ColumnDefinition } from '../semoss.types';
import { DataGrid, GridColDef } from '@mui/x-data-grid';

export interface ResultsGridProps {
    columns: ColumnDefinition[];
    result_set: object[];
    isLoading: boolean;
    error: boolean;
}

export const ResultsGrid = ({ columns }: ResultsGridProps) => {
    return (
        <DataGrid
            columns={columns.map(
                (col): GridColDef => ({
                    field: col.key,
                    headerName: col.label,
                }),
            )}
        />
    );
};
