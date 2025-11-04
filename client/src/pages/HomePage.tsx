import {
	Autocomplete,
	Button,
	Stack,
	TextField,
	Typography,
} from "@mui/material";
import { useCallback, useEffect, useState } from "react";
import { VectorFiles, VectorQuerySection } from "@/components";
import { useAppContext } from "@/contexts";
import { useLoadingState } from "@/hooks";
import type { Engine } from "@/types";

const embedderEngine = import.meta.env.CLIENT_EMBEDDER_ENGINE;
const trainingTag = "pro-code-training";

/**
 * Renders the home page.
 *
 * @component
 */
export const HomePage = () => {
	/**
	 * Library hooks
	 */
	const [isLoadingEngines, setIsLoadingEngines] = useLoadingState(false);
	const [isLoadingVectors, setIsLoadingVectors] = useLoadingState();
	const [isCreatingVector, setIsCreatingVector] = useLoadingState();
	const { runPixel } = useAppContext();

	/**
	 * State
	 */
	const [engines, setEngines] = useState<Engine[]>([]);
	const [vectors, setVectors] = useState<Engine[]>([]);
	const [newVectorName, setNewVectorName] = useState<string>("");
	const [selectedVector, setSelectedVector] = useState<Engine | null>(null);
	const [selectedModel, setSelectedModel] = useState<Engine | null>(null);

	/**
	 * Functions
	 */

	/**
	 * ACTIVITY #1: Fetch models
	 * 
	 * Instructions: 
	 *  - Replace the placeholder line below to fetch all models tagged "text-generation"
	 */

	const loadEngines = useCallback(async () => {}, [runPixel, setIsLoadingEngines]); // PLACEHOLDER - DELETE


	/**
	 * ACTIVITY #2: Fetch vectors
	 * 
	 * Instructions: 
	 *  - Replace placeholder line below to fetch vectors tagged for training (trainingTag)
	 */
	const loadVectors = useCallback(async () => {}, [runPixel, setIsLoadingVectors]); // PLACEHOLDER - DELETE THIS LINE


	/**
	 * TRAINING CHECK-IN POINT: Create Vector
	*/
	const createVector = async () => {
		const loadingKey = setIsCreatingVector(true);
		try {
			const newVector = await runPixel<{
				database_id: string;
				database_name: string;
			}>(
				`CreateVectorDatabaseEngine(database=${JSON.stringify(newVectorName)}, conDetails=[${JSON.stringify(
					{
						NAME: newVectorName,
						VECTOR_TYPE: "FAISS",
						EMBEDDER_ENGINE_ID: embedderEngine,
						INDEX_CLASSES: "default",
						CHUNKING_STRATEGY: "ALL",
						CONTENT_LENGTH: 512,
						CONTENT_OVERLAP: 20,
						DISTANCE_METHOD: "Squared Euclidean (L2) distance",
						RETAIN_EXTRACTED_TEXT: "false",
					},
				)}]);`,
			);
			await runPixel(
				`SetEngineMetadata(engine=${JSON.stringify(newVector.database_id)}, meta=[${JSON.stringify({ tag: trainingTag })}]);`,
			);
			setIsCreatingVector(false, loadingKey, () => {
				setNewVectorName("");
				loadVectors();
				setSelectedVector({
					app_id: newVector.database_id,
					app_name: newVector.database_name,
				});
			});
		} catch (e) {
			setIsCreatingVector(false, loadingKey);
			// Handle error
			console.error(e);
		}
	};

	/**
	 * Effects
	 */
	useEffect(() => {
		// Load available models on page load
		loadEngines();
	}, [loadEngines]);

	useEffect(() => {
		// Load available vectors on page load
		loadVectors();
	}, [loadVectors]);

	return (
		<Stack width="100%" alignItems="center" padding={4}>
			<Stack spacing={4} maxWidth="md" width="100%">
				<Stack spacing={2}>
					<Typography variant="h6">Settings</Typography>
					<Autocomplete
						options={engines || []}
						getOptionLabel={(option) => option.app_name}
						renderInput={(params) => (
							<TextField {...params} label="Model" />
						)}
						getOptionKey={(option) => option.app_id}
						loading={isLoadingEngines}
						value={selectedModel}
						onChange={(_, newValue) => setSelectedModel(newValue)}
						isOptionEqualToValue={(option, value) =>
							option.app_id === value.app_id
						}
					/>

					<Autocomplete
						isOptionEqualToValue={(option, value) =>
							option.app_id === value.app_id
						}
						value={selectedVector}
						onChange={(_, newValue) => setSelectedVector(newValue)}
						options={vectors || []}
						getOptionLabel={(option) => option.app_name}
						renderInput={(params) => (
							<TextField {...params} label="Vector" />
						)}
						getOptionKey={(option) => option.app_id}
						loading={isLoadingVectors || isCreatingVector}
					/>

					<Stack direction="row" spacing={2} alignItems="center">
						<TextField
							label="New Vector"
							fullWidth
							size="small"
							value={newVectorName}
							onChange={(e) => setNewVectorName(e.target.value)}
						/>
						<Button
							onClick={createVector}
							variant="contained"
							style={{ whiteSpace: "nowrap" }}
							disabled={!newVectorName}
							loading={isCreatingVector}
						>
							Create Vector
						</Button>
					</Stack>
				</Stack>
				<Stack spacing={2}>
					<Typography variant="h6">Documents</Typography>
					<VectorFiles vectorDbId={selectedVector?.app_id} />
				</Stack>
				<Stack spacing={2}>
					<Typography variant="h6">Summary</Typography>
					<VectorQuerySection
						vectorDbId={selectedVector?.app_id}
						modelId={selectedModel?.app_id}
					/>
				</Stack>
			</Stack>
		</Stack>
	);
};
