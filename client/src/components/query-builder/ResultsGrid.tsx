import { useEffect } from 'react';
import { ResultSet } from '../semoss.types';
import { DataGrid, GridColDef, useGridApiRef } from '@mui/x-data-grid';

export interface ResultsGridProps {
    resultSet?: ResultSet;
    isLoading: boolean;
    error: boolean;
    autosizeTrigger?: unknown; // Optional prop to trigger resize
}

const FRONT_END_ID = 'FRONT_END_ID';

export const ResultsGrid = ({
    error,
    resultSet,
    isLoading,
    autosizeTrigger,
}: ResultsGridProps) => {
    const apiRef = useGridApiRef();

    useEffect(() => {
        apiRef.current?.autosizeColumns({
            includeHeaders: true,
            includeOutliers: true,
        });
    }, [autosizeTrigger]);

    return (
        <>
            <DataGrid
                apiRef={apiRef}
                autosizeOnMount
                columns={
                    resultSet?.columns?.length && !error
                        ? resultSet.columns.map(
                              (col, index): GridColDef => ({
                                  field: col.key,
                                  headerName: col.key,
                                  sortable: false,
                                  disableColumnMenu: true,
                                  valueGetter: (_, row) => row[index],
                              }),
                          )
                        : [
                              {
                                  field: FRONT_END_ID,
                                  headerName: '',
                                  sortable: false,
                                  disableColumnMenu: true,
                                  valueGetter: () => '',
                              },
                          ]
                }
                rows={
                    resultSet?.rows.map((row, index) => ({
                        ...row,
                        [FRONT_END_ID]: index,
                    })) ?? []
                }
                getRowId={(row) => row[FRONT_END_ID]}
                loading={isLoading}
            />
        </>
    );
};
