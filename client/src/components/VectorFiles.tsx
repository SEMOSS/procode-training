import { Stack, styled, Typography } from "@mui/material";
import { useInsight } from "@semoss/sdk-react";
import { useCallback, useEffect, useState } from "react";
import { useAppContext } from "@/contexts";
import { useLoadingState, useSettingPixel } from "@/hooks";
import type { SemossFile } from "@/types";
import { FileRow } from "./FileRow";
import { Dropzone } from "./library";

const GrayStack = styled(Stack)(({ theme }) => ({
	backgroundColor: theme.palette.grey[100],
	padding: theme.spacing(2),
}));

export interface VectorFilesProps {
	vectorDbId?: string;
}

/**
 * Renders the vector files page, allowing users to view and add files
 *
 * @component
 */
export const VectorFiles = ({ vectorDbId }: VectorFilesProps) => {
	/**
	 * Library hooks
	 */
	const [runAddDocsPixel] = useSettingPixel();
	const { actions } = useInsight();
	const { runPixel } = useAppContext();
	const [isUploadingDocuments, setIsUploadingDocuments] = useLoadingState();
	const [isLoadingFiles, setIsLoadingFiles] = useLoadingState(false);
	const [isDeletingFile, setIsDeletingFile] = useLoadingState(false);
	const [files, setFiles] = useState<SemossFile[]>([]);

	/**
	 * Functions
	 */
	const loadFiles = useCallback(async () => {
		if (!vectorDbId) {
			setFiles([]);
			return;
		}

		const loadingKey = setIsLoadingFiles(true);
		try {
			/**
			 * TODO #3: list documents in vector DB
			 * 
			 * INSTRUCTIONS: 
			 * 	- Replace placeholder with implementation to list documents in selected vector database
			*/
			const newFiles = []; // PLACEHOLDER - DELETE THIS LINE

			setIsLoadingFiles(false, loadingKey, () => setFiles(newFiles));
		} catch {
			setIsLoadingFiles(false, loadingKey, () => setFiles([]));
		}
	}, [vectorDbId, runPixel, setIsLoadingFiles]);

	const handleNewFiles = async (newFiles: File[]) => {
		const loadingKey = setIsUploadingDocuments(true);
		const afterUpload = () => {
			setIsUploadingDocuments(false, loadingKey, loadFiles);
		};

		try {
			const uploadedFiles = await actions.upload(newFiles, "");
			const filePaths = uploadedFiles.map((file) =>
				file.fileLocation.slice(1),
			); // Removes leading '/'

			/**
			 * TODO #4: add documents to vector DB
			 * 
			 * INSTRUCTIONS:
			 * 	- Replace the placeholder code below to create embeddings from uploaded documents
			*/

			// PLACEHOLDER - DELETE
			console.log("Files uploaded but not yet processed into embeddings: ", filePaths);
			setTimeout(() => {
				afterUpload();
				alert("Complete TODO #4 using CreateEmbeddingsFromDocuments pixel to process uploaded filed into vector embeddings.");
			}, 500);
			// END PLACEHOLDER
		} catch {
			afterUpload();
		}
	};

	const deleteFile = async (file: SemossFile) => {
		const loadingKey = setIsDeletingFile(true);
		try {
			/**
			* TODO #5: remove document from vector DB
			* 
			* INSTRUCTIONS:
			* 	- Delete placeholder below to remove the document from selected vector database
			* 	- Implement: 
				await runPixel(
					`RemoveDocumentFromVectorDatabase(engine=${JSON.stringify(
						vectorDbId,
					)}, fileNames=${JSON.stringify([file.fileName])});`,
				);
			*/
			// PLACEHOLDER - DELETE
			console.log("Would delete file:", file.fileName);
			alert("Complete TODO #4 using RemoveDocumentFromVectorDatabase pixel to enable document deletion.")
			// END PLACEHOLDER
			setIsDeletingFile(false, loadingKey, loadFiles);
		} catch {
			setIsDeletingFile(false, loadingKey, loadFiles);
		}
	};

	/**
	 * Effects
	 */
	useEffect(() => {
		loadFiles();
	}, [loadFiles]);

	return (
		<GrayStack spacing={1}>
			<Dropzone
				handleNewFiles={handleNewFiles}
				disabled={
					!vectorDbId ||
					isUploadingDocuments ||
					isLoadingFiles ||
					isDeletingFile
				}
			/>
			{vectorDbId &&
			(isLoadingFiles || isDeletingFile || isUploadingDocuments) ? (
				<Typography>Loading...</Typography>
			) : (
				files.map((file) => (
					<FileRow
						key={file.fileName}
						file={file}
						onDelete={() => deleteFile(file)}
					/>
				))
			)}
		</GrayStack>
	);
};
