import { useLoadingState } from '@/hooks';
import { useInsight } from '@semoss/sdk-react';
import {
    createContext,
    PropsWithChildren,
    useCallback,
    useContext,
    useEffect,
    useState,
} from 'react';

export interface AppContextType {
    runPixel: <T = unknown>(pixelString: string) => Promise<T>;
    isAppDataLoading: boolean;
    onePlusTwo: number;
}

const AppContext = createContext<AppContextType | undefined>(undefined);

/**
 * Custom hook to get the stored app data and runPixel.
 *
 * @returns {AppContextType} - The data
 */
export const useAppContext = (): AppContextType => {
    const context = useContext(AppContext);
    if (!context) {
        throw new Error(
            'useAppContext must be used within an AppContextProvider',
        );
    }

    return context;
};

/**
 * Stores data accessible to the entire app. Must be used within an InsightProvider.
 *
 * @param {ReactNode} props.children The children who will have access to the app data.
 * @component
 */
export const AppContextProvider = ({ children }: PropsWithChildren) => {
    // Get the current state of the current insight
    const { actions, isReady } = useInsight();

    /**
     * State
     */
    const [isAppDataLoading, setIsAppDataLoading] = useLoadingState(true);
    // Example state variable to store the result of a pixel operation
    const [onePlusTwo, setOnePlusTwo] = useState<number>();

    /**
     * Functions
     */
    const runPixel = useCallback(
        async <T,>(pixelString: string) => {
            const response = actions.run<T[]>(pixelString);
            return (await response).pixelReturn[0].output;
        },
        [actions],
    );

    /**
     * Effects
     */
    useEffect(() => {
        // Function to load app data
        const loadAppData = async () => {
            const loadingKey = setIsAppDataLoading(true);

            // Define a type for the loader and setter pairs
            // This allows us to load multiple pieces of data simultaneously and set them in state
            interface LoadSetPair<T> {
                loader: () => Promise<T>;
                value?: T;
                setter: (value: T) => void;
            }

            // Create an array of loadSetPairs, each containing a loader function and a setter function
            const loadSetPairs: LoadSetPair<unknown>[] = [
                {
                    loader: async () => await runPixel<number>('1 + 2'),
                    setter: (response) => setOnePlusTwo(response),
                } satisfies LoadSetPair<number>,
            ];

            // Execute all loaders in parallel and wait for them all to complete
            await Promise.all(
                loadSetPairs.map(
                    async (loadSetPair) =>
                        (loadSetPair.value = await loadSetPair.loader()),
                ),
            );

            // Once all loaders have completed, set the loading state to false
            // and call each setter with the loaded value
            setIsAppDataLoading(false, loadingKey, () =>
                loadSetPairs.forEach((loadSetPair) =>
                    loadSetPair.setter(loadSetPair.value),
                ),
            );
        };

        if (isReady) {
            // If the insight is ready, then load the app data
            loadAppData();
        }
    }, [isReady, runPixel]);

    return (
        <AppContext.Provider value={{ runPixel, onePlusTwo, isAppDataLoading }}>
            {children}
        </AppContext.Provider>
    );
};
