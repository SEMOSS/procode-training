import { useLoadingPixel } from '@/hooks';
import { Stack, Typography } from '@mui/material';

/**
 * Renders the home page.
 *
 * @component
 */
export const HomePage = () => {

    return (
        <Stack spacing={2}>
            <Typography variant="h4">Home page</Typography>
            <Typography>
                Meeting minutes ...
            </Typography>
        </Stack>
    );
};
