import { useLoadingPixel, useLoadingState, useSettingPixel } from '@/hooks';
import { Stack, Typography } from '@mui/material';
import { Dropzone } from './library';
import { useInsight } from '@semoss/sdk-react';

export interface VectorFilesProps {
    engineId?: string;
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
export const VectorFiles = ({ engineId }: VectorFilesProps) => {
    /**
     * Library hooks
     */
    const [files, isLoadingFiles, loadFiles] = useLoadingPixel<SemossFile[]>(
        engineId
            ? `ListDocumentsInVectorDatabase(engine=${JSON.stringify(engineId)})`
            : undefined,
        [],
    );
    const [runAddDocsPixel] = useSettingPixel();
    const { actions } = useInsight();
    const [isUploadingDocuments, setIsUploadingDocuments] = useLoadingState();

    /**
     * Functions
     */
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
                `CreateEmbeddingsFromDocuments (engine = ${JSON.stringify(engineId)}, filePaths = ${JSON.stringify(filePaths)});`,
                afterUpload,
                afterUpload,
            );
        } catch {
            afterUpload();
        }
    };

    return !engineId || isLoadingFiles ? (
        'Loading...'
    ) : (
        <Stack>
            <Typography>Files in Vector Database:</Typography>
            <Dropzone
                handleNewFiles={handleNewFiles}
                disabled={isUploadingDocuments}
            />
            {files.map((file) => (
                <Stack
                    direction="row"
                    key={file.fileName}
                    spacing={2}
                    justifyContent="space-between"
                >
                    <Typography>{file.fileName}</Typography>
                    <Typography>{file.fileSize.toFixed(2)} KB</Typography>
                </Stack>
            ))}
        </Stack>
    );
};
