import { useLoadingPixel } from '@/hooks';
import { Stack, Typography } from '@mui/material';
import { useState } from 'react';

/**
 * Renders the home page.
 *
 * @component
 */
export const HomePage = () => {
    const [data, isLoading] = useLoadingPixel<string>('HelloUser()');

    const [test, setTest] = useState<{ b: string }>({ b: 'test' });

    return (
        <Stack spacing={2}>
            <Typography variant="h4">Home page</Typography>
            <Typography>
                Welcome to the SEMOSS Template application! This repository is
                meant to be a starting point for your own SEMOSS application.
            </Typography>
            <Typography variant="h6">Example pixel call:</Typography>
            <ul>
                <li>
                    <Typography variant="body1" fontWeight="bold">
                        HelloUser()
                    </Typography>
                    <ul>
                        <li>
                            <Typography fontStyle="italic">
                                {isLoading ? 'Loading...' : data}
                            </Typography>
                        </li>
                    </ul>
                </li>
            </ul>
            {test.b}
            <button onClick={() => setTest(null)}>error</button>
        </Stack>
    );
};
