import { Stack } from '@mui/material';
import { ColumnDefinition } from './semoss.types';

export interface QueryResultsProps {
    columns: ColumnDefinition[];
    result_set: object[];
    isLoading: boolean;
    error: boolean;
}

export const QueryResults = ({ isLoading, error }: QueryResultsProps) => {
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
