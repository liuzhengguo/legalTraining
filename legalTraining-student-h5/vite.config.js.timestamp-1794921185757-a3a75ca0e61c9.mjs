// vite.config.js
import { defineConfig } from "file:///C:/code/legalTraining/legalTraining-student-h5/node_modules/vite/dist/node/index.js";
import vue from "file:///C:/code/legalTraining/legalTraining-student-h5/node_modules/@vitejs/plugin-vue/dist/index.mjs";
import { readFileSync } from "node:fs";
var __vite_injected_original_import_meta_url = "file:///C:/code/legalTraining/legalTraining-student-h5/vite.config.js";
var { version } = JSON.parse(readFileSync(new URL("./package.json", __vite_injected_original_import_meta_url), "utf8"));
function releaseMetadata(release) {
  const source = JSON.stringify(release);
  return {
    name: "student-release-metadata",
    configureServer(server) {
      server.middlewares.use("/student-version.json", (_, response) => {
        response.setHeader("Content-Type", "application/json");
        response.setHeader("Cache-Control", "no-store");
        response.end(source);
      });
    },
    generateBundle() {
      this.emitFile({ type: "asset", fileName: "student-version.json", source });
    }
  };
}
var vite_config_default = defineConfig(({ command }) => {
  const release = { version, build: command === "build" ? (/* @__PURE__ */ new Date()).toISOString() : "development" };
  return {
    plugins: [vue(), releaseMetadata(release)],
    define: { __STUDENT_RELEASE__: JSON.stringify(release) },
    server: {
      port: 5173,
      proxy: {
        "/dev-api": {
          target: process.env.VITE_DEV_BACKEND || "http://localhost:8080",
          changeOrigin: true,
          rewrite: (p) => p.replace(/^\/dev-api/, "")
        }
      }
    },
    build: {
      outDir: "dist",
      chunkSizeWarningLimit: 800
    }
  };
});
export {
  vite_config_default as default
};
//# sourceMappingURL=data:application/json;base64,ewogICJ2ZXJzaW9uIjogMywKICAic291cmNlcyI6IFsidml0ZS5jb25maWcuanMiXSwKICAic291cmNlc0NvbnRlbnQiOiBbImNvbnN0IF9fdml0ZV9pbmplY3RlZF9vcmlnaW5hbF9kaXJuYW1lID0gXCJDOlxcXFxjb2RlXFxcXGxlZ2FsVHJhaW5pbmdcXFxcbGVnYWxUcmFpbmluZy1zdHVkZW50LWg1XCI7Y29uc3QgX192aXRlX2luamVjdGVkX29yaWdpbmFsX2ZpbGVuYW1lID0gXCJDOlxcXFxjb2RlXFxcXGxlZ2FsVHJhaW5pbmdcXFxcbGVnYWxUcmFpbmluZy1zdHVkZW50LWg1XFxcXHZpdGUuY29uZmlnLmpzXCI7Y29uc3QgX192aXRlX2luamVjdGVkX29yaWdpbmFsX2ltcG9ydF9tZXRhX3VybCA9IFwiZmlsZTovLy9DOi9jb2RlL2xlZ2FsVHJhaW5pbmcvbGVnYWxUcmFpbmluZy1zdHVkZW50LWg1L3ZpdGUuY29uZmlnLmpzXCI7aW1wb3J0IHsgZGVmaW5lQ29uZmlnIH0gZnJvbSAndml0ZSdcbmltcG9ydCB2dWUgZnJvbSAnQHZpdGVqcy9wbHVnaW4tdnVlJ1xuaW1wb3J0IHsgcmVhZEZpbGVTeW5jIH0gZnJvbSAnbm9kZTpmcydcblxuY29uc3QgeyB2ZXJzaW9uIH0gPSBKU09OLnBhcnNlKHJlYWRGaWxlU3luYyhuZXcgVVJMKCcuL3BhY2thZ2UuanNvbicsIGltcG9ydC5tZXRhLnVybCksICd1dGY4JykpXG5cbmZ1bmN0aW9uIHJlbGVhc2VNZXRhZGF0YShyZWxlYXNlKSB7XG4gIGNvbnN0IHNvdXJjZSA9IEpTT04uc3RyaW5naWZ5KHJlbGVhc2UpXG4gIHJldHVybiB7XG4gICAgbmFtZTogJ3N0dWRlbnQtcmVsZWFzZS1tZXRhZGF0YScsXG4gICAgY29uZmlndXJlU2VydmVyKHNlcnZlcikge1xuICAgICAgc2VydmVyLm1pZGRsZXdhcmVzLnVzZSgnL3N0dWRlbnQtdmVyc2lvbi5qc29uJywgKF8sIHJlc3BvbnNlKSA9PiB7XG4gICAgICAgIHJlc3BvbnNlLnNldEhlYWRlcignQ29udGVudC1UeXBlJywgJ2FwcGxpY2F0aW9uL2pzb24nKVxuICAgICAgICByZXNwb25zZS5zZXRIZWFkZXIoJ0NhY2hlLUNvbnRyb2wnLCAnbm8tc3RvcmUnKVxuICAgICAgICByZXNwb25zZS5lbmQoc291cmNlKVxuICAgICAgfSlcbiAgICB9LFxuICAgIGdlbmVyYXRlQnVuZGxlKCkge1xuICAgICAgdGhpcy5lbWl0RmlsZSh7IHR5cGU6ICdhc3NldCcsIGZpbGVOYW1lOiAnc3R1ZGVudC12ZXJzaW9uLmpzb24nLCBzb3VyY2UgfSlcbiAgICB9XG4gIH1cbn1cblxuLy8gXHU1REU1XHU3QTBCIEIgXHU1QjY2XHU3NTFGXHU3OUZCXHU1MkE4XHU3QUVGIFx1MDBCNyBcdTVGMDBcdTUzRDFcdTdBRUZcdTUzRTMgNTE3M1xuLy8gXHU3NTFGXHU0RUE3XHU4RDcwIFZJVEVfQVBJX0JBU0VcdUZGMDhodHRwczovL2FwaS54eHguZWR1LmNuXHVGRjA5XHVGRjBDXHU2NzJDXHU1NzMwXHU4RDcwIC9kZXYtYXBpIFx1NEVFM1x1NzQwNlxuZXhwb3J0IGRlZmF1bHQgZGVmaW5lQ29uZmlnKCh7IGNvbW1hbmQgfSkgPT4ge1xuICBjb25zdCByZWxlYXNlID0geyB2ZXJzaW9uLCBidWlsZDogY29tbWFuZCA9PT0gJ2J1aWxkJyA/IG5ldyBEYXRlKCkudG9JU09TdHJpbmcoKSA6ICdkZXZlbG9wbWVudCcgfVxuICByZXR1cm4ge1xuICAgIHBsdWdpbnM6IFt2dWUoKSwgcmVsZWFzZU1ldGFkYXRhKHJlbGVhc2UpXSxcbiAgICBkZWZpbmU6IHsgX19TVFVERU5UX1JFTEVBU0VfXzogSlNPTi5zdHJpbmdpZnkocmVsZWFzZSkgfSxcbiAgICBzZXJ2ZXI6IHtcbiAgICAgIHBvcnQ6IDUxNzMsXG4gICAgICBwcm94eToge1xuICAgICAgICAnL2Rldi1hcGknOiB7XG4gICAgICAgICAgdGFyZ2V0OiBwcm9jZXNzLmVudi5WSVRFX0RFVl9CQUNLRU5EIHx8ICdodHRwOi8vbG9jYWxob3N0OjgwODAnLFxuICAgICAgICAgIGNoYW5nZU9yaWdpbjogdHJ1ZSxcbiAgICAgICAgICByZXdyaXRlOiAocCkgPT4gcC5yZXBsYWNlKC9eXFwvZGV2LWFwaS8sICcnKVxuICAgICAgICB9XG4gICAgICB9XG4gICAgfSxcbiAgICBidWlsZDoge1xuICAgICAgb3V0RGlyOiAnZGlzdCcsXG4gICAgICBjaHVua1NpemVXYXJuaW5nTGltaXQ6IDgwMFxuICAgIH1cbiAgfVxufSlcbiJdLAogICJtYXBwaW5ncyI6ICI7QUFBb1UsU0FBUyxvQkFBb0I7QUFDalcsT0FBTyxTQUFTO0FBQ2hCLFNBQVMsb0JBQW9CO0FBRjhLLElBQU0sMkNBQTJDO0FBSTVQLElBQU0sRUFBRSxRQUFRLElBQUksS0FBSyxNQUFNLGFBQWEsSUFBSSxJQUFJLGtCQUFrQix3Q0FBZSxHQUFHLE1BQU0sQ0FBQztBQUUvRixTQUFTLGdCQUFnQixTQUFTO0FBQ2hDLFFBQU0sU0FBUyxLQUFLLFVBQVUsT0FBTztBQUNyQyxTQUFPO0FBQUEsSUFDTCxNQUFNO0FBQUEsSUFDTixnQkFBZ0IsUUFBUTtBQUN0QixhQUFPLFlBQVksSUFBSSx5QkFBeUIsQ0FBQyxHQUFHLGFBQWE7QUFDL0QsaUJBQVMsVUFBVSxnQkFBZ0Isa0JBQWtCO0FBQ3JELGlCQUFTLFVBQVUsaUJBQWlCLFVBQVU7QUFDOUMsaUJBQVMsSUFBSSxNQUFNO0FBQUEsTUFDckIsQ0FBQztBQUFBLElBQ0g7QUFBQSxJQUNBLGlCQUFpQjtBQUNmLFdBQUssU0FBUyxFQUFFLE1BQU0sU0FBUyxVQUFVLHdCQUF3QixPQUFPLENBQUM7QUFBQSxJQUMzRTtBQUFBLEVBQ0Y7QUFDRjtBQUlBLElBQU8sc0JBQVEsYUFBYSxDQUFDLEVBQUUsUUFBUSxNQUFNO0FBQzNDLFFBQU0sVUFBVSxFQUFFLFNBQVMsT0FBTyxZQUFZLFdBQVUsb0JBQUksS0FBSyxHQUFFLFlBQVksSUFBSSxjQUFjO0FBQ2pHLFNBQU87QUFBQSxJQUNMLFNBQVMsQ0FBQyxJQUFJLEdBQUcsZ0JBQWdCLE9BQU8sQ0FBQztBQUFBLElBQ3pDLFFBQVEsRUFBRSxxQkFBcUIsS0FBSyxVQUFVLE9BQU8sRUFBRTtBQUFBLElBQ3ZELFFBQVE7QUFBQSxNQUNOLE1BQU07QUFBQSxNQUNOLE9BQU87QUFBQSxRQUNMLFlBQVk7QUFBQSxVQUNWLFFBQVEsUUFBUSxJQUFJLG9CQUFvQjtBQUFBLFVBQ3hDLGNBQWM7QUFBQSxVQUNkLFNBQVMsQ0FBQyxNQUFNLEVBQUUsUUFBUSxjQUFjLEVBQUU7QUFBQSxRQUM1QztBQUFBLE1BQ0Y7QUFBQSxJQUNGO0FBQUEsSUFDQSxPQUFPO0FBQUEsTUFDTCxRQUFRO0FBQUEsTUFDUix1QkFBdUI7QUFBQSxJQUN6QjtBQUFBLEVBQ0Y7QUFDRixDQUFDOyIsCiAgIm5hbWVzIjogW10KfQo=
