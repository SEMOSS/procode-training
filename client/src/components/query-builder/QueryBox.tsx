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
    result_set: [],
    columns: [],
};

export const QueryBox = () => {
    const { models, databases, runPixel } = useAppContext();

    /**
     * State
     */
    const [model, setModel] = useState<Engine>(null);
    const [db, setDb] = useState<Engine>(null);
    const [question, setQuestion] = useState<string>('');
    const [isResponseLoading, setIsResponseLoading] = useLoadingState(false);
    const [error, setError] = useState<boolean>(false);
    const [response, setResponse] = useState<ReactorResponse>(blankResponse);

    /**
     * Functions
     */
    const submitPrompt = async () => {
        const loadingKey = setIsResponseLoading(true);
        try {
            const response = await runPixel<ReactorResponse>(
                `QueryDatabase( engine = ${JSON.stringify(model.app_id)}, command = ${JSON.stringify(question)}, database = ${JSON.stringify(db.app_id)} )`,
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
    const isSubmitDisabled = !model?.app_id || !db?.app_id || !question.trim();

    return (
        <StyledStack padding={2} width="100%" maxWidth="md" spacing={2}>
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
                    Query
                </Typography>

                <TextField
                    label="Text"
                    multiline
                    rows={3}
                    value={question}
                    onChange={(e) => setQuestion(e.target.value)}
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
                    Results
                </Typography>

                <TextField
                    label="SQL"
                    multiline
                    minRows={3}
                    maxRows={6}
                    value={response.sql}
                    disabled
                />

                <TextField
                    label="Explanation"
                    multiline
                    rows={3}
                    value={response.explanation}
                    disabled
                />

                <ResultsGrid
                    error={error}
                    isLoading={isResponseLoading}
                    columns={
                        response.columns?.length
                            ? response.columns
                            : [
                                  {
                                      key: 'USER_ID',
                                      label: 'User ID',
                                      type: 'string',
                                  },
                              ]
                    }
                    result_set={response.result_set}
                />
            </Stack>
        </StyledStack>
    );
};
