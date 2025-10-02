import { useAppContext } from '@/contexts';
import { useLoadingState } from '@/hooks';
import { Button, Stack, TextField } from '@mui/material';
import { useState } from 'react';

export interface VectorQuerySectionProps {
    vectorDbId?: string;
    modelId?: string;
}

/**
 * Renders the vector query section
 *
 * @component
 */
export const VectorQuerySection = ({
    vectorDbId,
    modelId,
}: VectorQuerySectionProps) => {
    /**
     * Library hooks
     */
    const [isLoading, setIsLoading] = useLoadingState(false);
    const { runPixel } = useAppContext();

    /**
     * State
     */
    const [result, setResult] = useState<string>('');
    const [error, setError] = useState<boolean>(null);

    /**
     * Functions
     */
    const summarize = async () => {
        const loadingKey = setIsLoading(true);

        try {
            const chunks = await runPixel<{ Content: string }[]>(
                `VectorDatabaseQuery(engine="${vectorDbId}", command="What are the key points of this document?", limit=3)`,
            );
            const contentToSummarize = chunks
                .map((c) => c.Content)
                .join('\n\n');

            const responseObject = await runPixel<{ response: string }>(
                `LLM2(engine="${modelId}", command=${JSON.stringify(
                    `You are an assistant tasked with summarizing a document. Given the following chunks of text from the document, provide a detailed summary:\n\n${contentToSummarize}`,
                )});`,
            );

            setIsLoading(false, loadingKey, () => {
                setResult(responseObject.response);
                setError(false);
            });
        } catch {
            setIsLoading(false, loadingKey, () => {
                setResult('Error during summarization');
                setError(true);
            });
        }
    };

    return (
        <Stack spacing={2}>
            <Button
                variant="contained"
                disabled={!vectorDbId || !modelId}
                onClick={summarize}
                loading={isLoading}
            >
                Summarize
            </Button>
            <TextField
                value={result}
                disabled={!result || isLoading || error}
                multiline
                minRows={4}
            />
        </Stack>
    );
};
