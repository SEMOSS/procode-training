import { QueryBox } from '@/components';
import { Stack } from '@mui/material';

/**
 * Renders the home page.
 *
 * @component
 */
export const HomePage = () => {
    return (
        <Stack width="100%" alignItems="center">
            <QueryBox />
        </Stack>
    );
};
