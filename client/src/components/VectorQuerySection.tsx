import { Button, Stack, TextField } from "@mui/material";
import { useState } from "react";
import { useAppContext } from "@/contexts";
import { useLoadingState } from "@/hooks";

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
	const [result, setResult] = useState<string>("");
	const [error, setError] = useState<boolean>(null);

	/**
	 * Functions
	 */
	const summarize = async () => {
		const loadingKey = setIsLoading(true);

		try {
			/**
			 * TODO #6: query vector DB for main points from document
			 * 
			 * INSTRUCTIONS: 
			 * 	- Replace the placeholder with vector DB query to get chunks with the key points from the document
			*/
			const chunks = []; // PLACEHOLDER - DELETE THIS LINE

			const contentToSummarize = chunks
				.map((c) => c.Content)
				.join("\n\n");

			/**
			 * TODO #7: prompt LLM with context from vector DB
			 * 
			 * INSTRUCTIONS: 
			 * 	- Replace the placeholder line to prompt LLM to generate an exectutive summary based on chunks from the document
			 */
			const responseObject = { response: `Retrieved ${chunks.length} chunks.`}; // PLACEHOLDER - DELETE THIS LINE
			setIsLoading(false, loadingKey, () => {
				setResult(responseObject.response);
				setError(false);
			});
		} catch {
			setIsLoading(false, loadingKey, () => {
				setResult("Error during summarization");
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
