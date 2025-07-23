import { Engine } from '@/components';
import { useAppContext } from '@/contexts';
import { useLoadingState } from '@/hooks';
import {
    Autocomplete,
    Button,
    Stack,
    styled,
    TextField,
    Typography,
} from '@mui/material';
import { useState } from 'react';

const StyledStack = styled(Stack)(({ theme }) => ({
    backgroundColor: theme.palette.background.paper,
    borderRadius: theme.shape.borderRadius,
}));

/**
 * Renders the home page.
 *
 * @component
 */
export const HomePage = () => {
    const { models, databases, runPixel } = useAppContext();

    /**
     * State
     */
    const [model, setModel] = useState<Engine>(null);
    const [db, setDb] = useState<Engine>(null);
    const [prompt, setPrompt] = useState<string>('');
    const [response, setResponse] = useState<string>('');
    const [isResponseLoading, setIsResponseLoading] = useLoadingState(false);
    const [error, setError] = useState<boolean>(false);

    /**
     * Functions
     */
    const submitPrompt = async () => {
        const loadingKey = setIsResponseLoading(true);
        try {
            const response = await runPixel<string>(
                `LLM2( engine = ${JSON.stringify(model.app_id)}, command = ${JSON.stringify(prompt)} )`,
            );
            setIsResponseLoading(false, loadingKey, () =>
                setResponse(response),
            );
        } catch {
            setIsResponseLoading(false, loadingKey, () => setError(true));
        }
    };

    /**
     * Constants
     */
    const isSubmitDisabled = !model?.app_id || !db?.app_id || !prompt.trim();

    return (
        <Stack width="100%" alignItems="center">
            <StyledStack
                padding={2}
                width={{ xs: '100%', md: '50%' }}
                spacing={2}
            >
                <Stack spacing={1}>
                    <Typography variant="h6" fontWeight="bold">
                        Options
                    </Typography>

                    <Autocomplete
                        value={model}
                        onChange={(_, val) => setModel(val)}
                        options={models}
                        getOptionKey={(model) => model.app_id}
                        getOptionLabel={(model) => model.app_name}
                        renderInput={(params) => (
                            <TextField {...params} label="Model" />
                        )}
                        fullWidth
                    />

                    <Autocomplete
                        value={db}
                        onChange={(_, val) => setDb(val)}
                        options={databases}
                        getOptionKey={(db) => db.app_id}
                        getOptionLabel={(db) => db.app_name}
                        renderInput={(params) => (
                            <TextField {...params} label="Database" />
                        )}
                        fullWidth
                    />
                </Stack>

                <Stack spacing={1}>
                    <Typography variant="h6" fontWeight="bold">
                        Prompt
                    </Typography>

                    <TextField
                        multiline
                        rows={3}
                        value={prompt}
                        onChange={(e) => setPrompt(e.target.value)}
                    />
                </Stack>

                <Button
                    variant="contained"
                    onClick={submitPrompt}
                    loading={isResponseLoading}
                    disabled={isSubmitDisabled}
                >
                    Submit
                </Button>

                <Stack spacing={1}>
                    <Typography variant="h6" fontWeight="bold">
                        Response
                    </Typography>

                    <TextField
                        multiline
                        minRows={3}
                        value={
                            isResponseLoading
                                ? 'Loading...'
                                : error
                                  ? 'An error occurred. Please try again later. '
                                  : response
                        }
                    />
                </Stack>
            </StyledStack>
        </Stack>
    );
};
