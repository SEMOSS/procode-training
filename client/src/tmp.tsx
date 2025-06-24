import { useAppContext } from './contexts';

export const Tmp = () => {
    const { onePlusTwo } = useAppContext();

    return <div>{`1 + 2 = ${onePlusTwo}`}</div>;
};
