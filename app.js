(() => {
  const names = {dashboard:"世界工作台",maps:"地图数据",builder:"世界生成",library:"资源库",export:"导出中心"};
  const toast = document.getElementById("toast");
  let timer;
  function notify(message){toast.textContent=message;toast.classList.add("show");clearTimeout(timer);timer=setTimeout(()=>toast.classList.remove("show"),2500);}
  function showPage(page){
    if(!names[page])return;
    document.querySelectorAll(".page").forEach(el=>el.classList.toggle("active",el.id==="page-"+page));
    document.querySelectorAll(".nav").forEach(el=>el.classList.toggle("active",el.dataset.page===page));
    document.getElementById("crumb").textContent=names[page];
    window.scrollTo({top:0,behavior:"smooth"});
  }
  document.querySelectorAll("[data-page]").forEach(el=>el.addEventListener("click",()=>showPage(el.dataset.page)));
  document.querySelectorAll("[data-page-link]").forEach(el=>el.addEventListener("click",event=>{event.preventDefault();showPage(el.dataset.pageLink);}));
  document.querySelectorAll("[data-toast]").forEach(el=>el.addEventListener("click",()=>notify(el.dataset.toast)));

  const file=document.getElementById("mapFile"),zone=document.getElementById("dropzone"),fileName=document.getElementById("fileName");
  file.addEventListener("change",()=>{const f=file.files&&file.files[0];if(!f)return;fileName.textContent=f.name+" · "+(f.size<1048576?(f.size/1024).toFixed(1)+" KB":(f.size/1048576).toFixed(2)+" MB")+"（仅本地选择，尚未解析）";notify("文件已选择；当前原型不会上传或解析它。");});
  ["dragenter","dragover"].forEach(t=>zone.addEventListener(t,e=>{e.preventDefault();zone.classList.add("drag");}));
  ["dragleave","drop"].forEach(t=>zone.addEventListener(t,e=>{e.preventDefault();zone.classList.remove("drag");}));
  zone.addEventListener("drop",e=>{const f=e.dataTransfer&&e.dataTransfer.files[0];if(f){fileName.textContent=f.name+"（已拖入，仅显示文件名）";notify("已接收文件名称，数据导入引擎尚未接入。");}});

  document.getElementById("quickForm").addEventListener("submit",e=>{
    e.preventDefault();
    const name=document.getElementById("worldName").value.trim()||"未命名世界";
    const size=Number(document.getElementById("worldSize").value).toLocaleString("en-US");
    const edition=document.getElementById("edition").value;
    const row=document.createElement("article");row.className="project";
    row.innerHTML='<div class="thumb city"><small>NEW</small><i></i></div><div class="project-copy"><b></b><span></span><small>● 新建草稿 · 刚刚</small></div><button class="more">···</button>';
    row.querySelector(".project-copy b").textContent=name;row.querySelector(".project-copy span").textContent=edition+" · "+size+" × "+size;
    row.querySelector(".more").addEventListener("click",()=>notify("项目管理功能将在后续版本开放。"));
    document.getElementById("projectList").prepend(row);
    document.getElementById("projectCount").textContent=String(document.querySelectorAll("#projectList .project").length).padStart(2,"0");
    notify("项目草稿已添加；真实地图生成引擎尚未接入。");
  });
  document.querySelectorAll("#styles .style").forEach(el=>el.addEventListener("click",()=>{
    document.querySelectorAll("#styles .style").forEach(x=>x.classList.remove("selected"));el.classList.add("selected");document.getElementById("sumStyle").textContent=el.dataset.style;
  }));
  function syncSummary(){document.getElementById("sumSize").textContent=document.getElementById("builderSize").value;document.getElementById("sumEdition").textContent=document.getElementById("builderEdition").value;}
  document.getElementById("builderSize").addEventListener("change",syncSummary);document.getElementById("builderEdition").addEventListener("change",syncSummary);
  document.getElementById("saveConfig").addEventListener("click",()=>notify("配置已更新到当前界面；持久化保存尚未接入。"));
})();
