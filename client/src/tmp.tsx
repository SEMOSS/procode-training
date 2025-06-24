import { useAppContext } from "./contexts"

export const Tmp = () => {
    const { runPixel } = useAppContext();
    runPixel("hi");

    return <div>Hello world</div>
}