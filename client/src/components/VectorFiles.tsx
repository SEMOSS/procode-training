import { useLoadingPixel, useLoadingState, useSettingPixel } from '@/hooks';
import { Stack, Typography } from '@mui/material';
import { Dropzone } from './library';
import { useInsight } from '@semoss/sdk-react';

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
    const [files, isLoadingFiles, loadFiles] = useLoadingPixel<SemossFile[]>(
        vectorDbId
            ? `ListDocumentsInVectorDatabase(engine=${JSON.stringify(vectorDbId)})`
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
                `CreateEmbeddingsFromDocuments (engine = ${JSON.stringify(vectorDbId)}, filePaths = ${JSON.stringify(filePaths)});`,
                afterUpload,
                afterUpload,
            );
        } catch {
            afterUpload();
        }
    };

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
