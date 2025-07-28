import {
    Autocomplete,
    Button,
    Stack,
    styled,
    TextField,
    Typography,
} from '@mui/material';
import { Engine, ReactorResponse } from '../semoss.types';
import { useAppContext } from '@/contexts';
import { useState } from 'react';
import { useLoadingState } from '@/hooks';
import { ResultsGrid } from './ResultsGrid';

const StyledStack = styled(Stack)(({ theme }) => ({
    backgroundColor: theme.palette.background.paper,
    borderRadius: theme.shape.borderRadius,
}));

const blankResponse: ReactorResponse = {
    question: '',
    explanation: ' ',
    sql: ' ',
    result_set: {
        rows: [],
        columns: [],
    },
};

export const QueryBox = () => {
    const { models, databases, runPixel } = useAppContext();

    /**
     * State
     */
    const [model, setModel] = useState<Engine>(models[0] ?? null);
    const [db, setDb] = useState<Engine>(databases[0] ?? null);
    const [question, setQuestion] = useState<string>('');
    const [isResponseLoading, setIsResponseLoading] = useLoadingState(false);
    const [error, setError] = useState<boolean>(false);
    const [response, setResponse] = useState<ReactorResponse>(blankResponse);
    const [autosizeTrigger, setAutosizeTrigger] = useState<number>(0);

    /**
     * Functions
     */
    const submitPrompt = async () => {
        const loadingKey = setIsResponseLoading(true);
        try {
            const response = await runPixel<ReactorResponse>(
                `QueryDatabase( engine = ${JSON.stringify(model.app_id)}, command = ${JSON.stringify(question)}, database = ${JSON.stringify(db.app_id)} )`,
            );
            setIsResponseLoading(false, loadingKey, () => {
                setError(false);
                setResponse(response);
            });
        } catch {
            setIsResponseLoading(false, loadingKey, () => setError(true));
        }
    };

    /**
     * Constants
     */
    const isSubmitDisabled = !model?.app_id || !db?.app_id || !question.trim();

    return (
        <StyledStack padding={2} width="100%" maxWidth="md" spacing={2}>
            <Stack spacing={1}>
                <Typography variant="h6" fontWeight="bold">
                    Prompt
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
                    disabled={isResponseLoading}
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
                    disabled={isResponseLoading}
                />

                <TextField
                    label="Text"
                    multiline
                    minRows={3}
                    maxRows={6}
                    value={question}
                    onChange={(e) => setQuestion(e.target.value)}
                    disabled={isResponseLoading}
                />

                <Button
                    variant="contained"
                    onClick={submitPrompt}
                    loading={isResponseLoading}
                    disabled={isSubmitDisabled}
                >
                    Submit
                </Button>
            </Stack>

            <Stack spacing={1}>
                <Typography variant="h6" fontWeight="bold">
                    Query
                </Typography>

                <TextField
                    label="SQL"
                    multiline
                    minRows={3}
                    maxRows={6}
                    value={
                        error
                            ? 'An error occurred while generating the results.'
                            : response.sql
                    }
                    disabled
                />

                <TextField
                    label="Explanation"
                    multiline
                    minRows={3}
                    maxRows={6}
                    value={
                        error
                            ? 'An error occurred while generating the results.'
                            : response.explanation
                    }
                    disabled
                />
            </Stack>

            <Stack spacing={1}>
                <Stack
                    direction="row"
                    alignItems="center"
                    justifyContent="space-between"
                    spacing={1}
                >
                    <Typography variant="h6" fontWeight="bold">
                        Data
                    </Typography>

                    <Button
                        variant="outlined"
                        disabled={
                            isResponseLoading ||
                            error ||
                            !response.result_set?.columns.length
                        }
                        onClick={() => setAutosizeTrigger((prev) => prev + 1)}
                    >
                        Autosize columns
                    </Button>
                </Stack>

                <ResultsGrid
                    error={error}
                    isLoading={isResponseLoading}
                    resultSet={response.result_set}
                    autosizeTrigger={autosizeTrigger}
                />
            </Stack>
        </StyledStack>
    );
};
