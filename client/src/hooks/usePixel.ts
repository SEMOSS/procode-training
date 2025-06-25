import { useCallback, useEffect, useState } from 'react';
import { useLoadingState } from './useLoadingState';
import { useAppContext } from '@/contexts';

export const usePixel = <T>(
    pixelString: string,
    initialValue?: T,
): [T, boolean, () => void] => {
    const { runPixel } = useAppContext();

    const [isLoading, setIsLoading] = useLoadingState();
    const [response, setResponse] = useState<T>(initialValue);

    const fetchPixel = useCallback(() => {
        (async () => {
            const loadingKey = setIsLoading(true);
            const response = await runPixel<T>(pixelString);
            setIsLoading(false, loadingKey, () => setResponse(response));
        })();
    }, [pixelString, setIsLoading, runPixel]);

    useEffect(() => {
        fetchPixel();
    }, [fetchPixel]);

    return [response, isLoading, fetchPixel];
};
