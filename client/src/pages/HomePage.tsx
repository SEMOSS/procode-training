import { useLoadingPixel } from '@/hooks';
import { Autocomplete, Stack, TextField, Typography } from '@mui/material';

interface Engine {
    app_id: string;
    app_name: string;
}

/**
 * Renders the home page.
 *
 * @component
 */
export const HomePage = () => {
    /**
     * Library hooks
     */
    const [engines, isLoadingEngines] = useLoadingPixel<Engine[]>(
        `MyEngines( engineTypes=["MODEL"] )`,
    );

    console.log(engines, isLoadingEngines);

    return (
        <Stack spacing={2}>
            <Typography variant="h4">Home page</Typography>
            <Typography>Meeting minutes ...</Typography>
            {isLoadingEngines ? (
                <Typography>Loading engines...</Typography>
            ) : (
                <>
                    <Autocomplete
                        options={engines || []}
                        getOptionLabel={(option) => option.app_name}
                        renderInput={(params) => (
                            <TextField {...params} label="Model" />
                        )}
                        getOptionKey={(option) => option.app_id}
                    />
                </>
            )}
        </Stack>
    );
};
