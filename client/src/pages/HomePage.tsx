import { useLoadingPixel } from '@/hooks';
import { Stack, Typography } from '@mui/material';

/**
 * Renders the home page.
 *
 * @component
 */
export const HomePage = () => {
    const [data, isLoading] = useLoadingPixel<string>('HelloWorld()');

    return (
        <Stack spacing={2}>
            <Typography variant="h4">Home page</Typography>
            <Typography>
                Welcome to the SEMOSS Template application! This is a starting
                point for building your own SEMOSS application.
            </Typography>
            <Typography variant="h6">Example of a pixel call:</Typography>
            <Typography>{isLoading ? 'Loading...' : data}</Typography>
        </Stack>
    );
};
