import { useLoadingState, useSettingPixel } from '@/hooks';
import { Stack, Typography } from '@mui/material';
import { Dropzone } from './library';
import { useInsight } from '@semoss/sdk-react';
import { useCallback, useEffect, useState } from 'react';
import { useAppContext } from '@/contexts';

export interface VectorFilesProps {
    vectorDbId?: string;
}

interface SemossFile {
    fileName: string;
    fileSize: number; // in KB
    lastModified: string; // "YYYY-MM-DD HH:mm:ss"
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
    }, [vectorDbId]);

    const handleNewFiles = async (newFiles: File[]) => {
        const loadingKey = setIsUploadingDocuments(true);
        const afterUpload = () => {
            setIsUploadingDocuments(false, loadingKey, loadFiles);
        };

        try {
            const uploadedFiles = await actions.upload(newFiles, '');
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

    /**
     * Effects
     */
    useEffect(() => {
        loadFiles();
    }, [loadFiles]);

    return (
        <Stack>
            <Dropzone
                handleNewFiles={handleNewFiles}
                disabled={isUploadingDocuments}
            />
            {vectorDbId && isLoadingFiles ? (
                <Typography>Loading...</Typography>
            ) : (
                files.map((file) => (
                    <Stack
                        direction="row"
                        key={file.fileName}
                        spacing={2}
                        justifyContent="space-between"
                    >
                        <Typography>{file.fileName}</Typography>
                        <Typography>{file.fileSize.toFixed(2)} KB</Typography>
                    </Stack>
                ))
            )}
        </Stack>
    );
};
