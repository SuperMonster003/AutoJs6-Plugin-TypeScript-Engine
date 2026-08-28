"ui";

console.log(`Starting TSX UI on a ${device.width}px screen`);

ui.layout(
  <vertical gravity="center" padding={24}>
    <>
      <text id="status" textSize="20sp">TypeScript TSX template</text>
      <button onClick={() => {
        console.log("TSX template button clicked");
        toast("Rhino TSX template works");
      }}>Click me</button>
    </>
  </vertical>,
);
