import { createContext, PropsWithChildren, useContext } from 'react';

export interface AppContextType {
    runPixel: <T = unknown>(pixelString: string) => T;
}

const AppContext = createContext<AppContextType | undefined>(undefined);

export const useAppContext = () => {
    const context = useContext(AppContext);
    if (!context) {
        throw new Error(
            'useAppContext must be used within an AppContextProvider',
        );
    }




    
    return context;
};

export const AppContextProvider = ({ children }: PropsWithChildren) => {
    const runPixel = <T,>(pixelString) => {
        console.log(pixelString);
        return null as T;
    };

    return (
        <AppContext.Provider value={{ runPixel }}>
            {children}
        </AppContext.Provider>
    );
};
