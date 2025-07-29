import { DataGrid } from '@mui/x-data-grid';
import { Animal } from './examples.types';

export interface AnimalListProps {
    animalList: Animal[];
    loading?: boolean;
}

/**
 * Display a list of animals.
 *
 * @component
 */
export const AnimalList = ({ animalList, loading }: AnimalListProps) => {
    return (
        <DataGrid
            loading={loading}
            columns={[
                { field: 'animal_id', headerName: 'ID', type: 'number' },
                { field: 'animal_name', headerName: 'Name', type: 'string' },
                { field: 'animal_type', headerName: 'Type', type: 'string' },
            ]}
            rows={animalList}
            getRowId={(row) => row.animal_id}
        />
    );
};
