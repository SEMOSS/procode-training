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
			const newFiles = await runPixel<SemossFile[]>(
				`ListDocumentsInVectorDatabase(engine=${JSON.stringify(vectorDbId)})`,
			);
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
			); // Remove leading '/'
			runAddDocsPixel(
				`CreateEmbeddingsFromDocuments (engine = ${JSON.stringify(vectorDbId)}, filePaths = ${JSON.stringify(filePaths)});`,
				afterUpload,
				afterUpload,
			);
		} catch {
			afterUpload();
		}
	};

	const deleteFile = async (file: SemossFile) => {
		const loadingKey = setIsDeletingFile(true);
		try {
			await runPixel(
				`RemoveDocumentFromVectorDatabase(engine=${JSON.stringify(
					vectorDbId,
				)}, fileNames=${JSON.stringify([file.fileName])});`,
			);
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
