import { ResultSet } from '../semoss.types';
import { DataGrid, GridColDef } from '@mui/x-data-grid';

export interface ResultsGridProps {
    resultSet: ResultSet;
    isLoading: boolean;
    error: boolean;
}

const FRONT_END_ID = 'FRONT_END_ID';

export const ResultsGrid = ({
    error,
    resultSet,
    isLoading,
}: ResultsGridProps) => {
    return error ? (
        'Error loading results'
    ) : !resultSet.columns?.length ? (
        'No results found'
    ) : (
        <DataGrid
            columns={resultSet.columns.map(
                (col, index): GridColDef => ({
                    field: col.key,
                    headerName: col.key,
                    sortable: false,
                    disableColumnMenu: true,
                    valueGetter: (row) => row[index],
                }),
            )}
            rows={resultSet.rows.map((row, index) => ({
                ...row,
                [FRONT_END_ID]: index,
            }))}
            getRowId={(row) => row[FRONT_END_ID]}
            loading={isLoading}
        />
    );
};
