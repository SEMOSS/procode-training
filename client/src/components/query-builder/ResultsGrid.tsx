import { Stack } from '@mui/material';
import { ColumnDefinition } from '../semoss.types';

export interface ResultsGridProps {
    columns: ColumnDefinition[];
    result_set: object[];
    isLoading: boolean;
    error: boolean;
}

export const ResultsGrid = ({ isLoading, error }: ResultsGridProps) => {
    return (
        <Stack>
            {isLoading
                ? 'Loading...'
                : error
                  ? 'Error loading results'
                  : 'Results loaded successfully'}
        </Stack>
    );
};
