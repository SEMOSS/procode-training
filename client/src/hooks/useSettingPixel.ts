import { useCallback } from 'react';
import { useLoadingState } from './useLoadingState';
import { useAppContext } from '@/contexts';

/**
 * Custom hook to call a reactor, typically used for calling a pixel on a click
 *
 * @template T The expected type of the pixel response.
 * @returns {[boolean, (pixelString: string, onSuccess?: (response: T) => Promise<void>, onError?: (error: Error) => Promise<void>) => void]} A function to call the pixel and a boolean indicating if the pixel is loading.
 */
export const useSettingPixel = <T>(): [
    boolean,
    (
        pixelString: string,
        onSuccess?: (response: T) => Promise<void>,
        onError?: (error: Error) => Promise<void>,
    ) => void,
] => {
    const { runPixel } = useAppContext();

    /**
     * State
     */
    const [isLoading, setIsLoading] = useLoadingState();

    /**
     * Functions
     */
    const fetchPixel = useCallback(
        (
            pixelString: string,
            onSuccess?: (response: T) => Promise<void>,
            onError?: (error: Error) => Promise<void>,
        ) => {
            (async () => {
                const loadingKey = setIsLoading(true);
                try {
                    const response = await runPixel<T>(pixelString);
                    setIsLoading(false, loadingKey, () =>
                        onSuccess?.(response),
                    );
                } catch (error) {
                    setIsLoading(false, loadingKey, () => onError?.(error));
                }
            })();
        },
        [setIsLoading, runPixel],
    );

    return [isLoading, fetchPixel];
};
