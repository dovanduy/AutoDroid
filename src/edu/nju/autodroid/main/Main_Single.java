package edu.nju.autodroid.main;

import com.android.ddmlib.*;
import edu.nju.autodroid.androidagent.AdbAgent;
import edu.nju.autodroid.androidagent.IAndroidAgent;
import edu.nju.autodroid.strategy.DepthGroupWeightedStrategy;
import edu.nju.autodroid.strategy.GroupWeightedSelectionStrategy;
import edu.nju.autodroid.strategy.IStrategy;
import edu.nju.autodroid.strategy.PagedWindowWeightSelectionStrategy;
import edu.nju.autodroid.uiautomator.UiautomatorClient;
import edu.nju.autodroid.utils.AdbTool;
import edu.nju.autodroid.utils.Configuration;
import edu.nju.autodroid.utils.Logger;
import edu.nju.autodroid.utils.UiAutomatorTool;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Created by ysht on 2016/9/26.
 */
public class Main_Single {
    public static void main(String[] args) throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException, InterruptedException {
        if(args.length != 2 ){
            System.out.println("Usage: java -jar AutoDroid.jar <Mode> <APK-Folder-path>");
            return;
        }
        int mode = Integer.parseInt(args[0]);
        if(mode == 0)
        {
            Main.main(args);
            return;
        }

        DdmPreferences.setTimeOut(10000);
        AdbTool.initializeBridge();

        List<String> apkFileList = Main.getApkFileList(args[1]);
        Logger.logInfo("Total Apk counts：" + apkFileList.size());

        IDevice device = AdbTool.getDefaultDevice();//使用默认的device


        for(String apkFilePath : apkFileList){
            List<String> finishedList = Main.getFinishedList("finishedList.txt");
            if(finishedList.contains(apkFilePath))
                continue;
            int uiautomatorTaskId = AdbTool.getTaskId(device, "uiautomator");
            if(uiautomatorTaskId > 0)
                AdbTool.killTask(device, uiautomatorTaskId);
            Thread.sleep(2000);
            if(AdbTool.getTaskId(device, "uiautomator") < 0) {//如果uiautomator没有启动
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        UiautomatorClient.start(device.getSerialNumber(), UiautomatorClient.PHONE_PORT);
                    }
                }).start();
            }
            while (AdbTool.getTaskId(device, "uiautomator") < 0){//等待uiautomator
                Logger.logInfo("Waiting for Uiautomator...");
                Thread.sleep(1000);
            }
            Logger.logInfo("UiAutomator start successfully!");



            File apkFile = new File(apkFilePath);
            IAndroidAgent agent = new AdbAgent(device, UiautomatorClient.PHONE_PORT, UiautomatorClient.PHONE_PORT);
            boolean result;
            result = agent.init();
            Logger.logInfo("Init agent："+result);
            String packageName = AdbTool.getPackageFromApk(apkFilePath);
           // if(!AdbTool.hasInstalledPackage(agent.getDevice(), packageName))
            {
                result = AdbTool.installApk(agent.getDevice().getSerialNumber(), apkFilePath);
                Logger.logInfo("Install apk："+result);
            }

            if(result){
                String laubchableActivity = AdbTool.getLaunchableAcvivity(apkFilePath);

                if(!laubchableActivity.endsWith("/")) {
                    String apkName = apkFile.getName().substring(0, apkFile.getName().lastIndexOf('.'));
                    IStrategy strategy = new DepthGroupWeightedStrategy(agent, Configuration.getMaxStep(), laubchableActivity, new Logger(apkName, "logger_output\\" + apkName + ".txt"));//"com.financial.calculator/.FinancialCalculators"
                    Logger.logInfo("Start Strategy：" + strategy.getStrategyName());
                    Logger.logInfo("Strategy target：" + apkFilePath);
                    try{
                        if (strategy.run()) {
                            Logger.logInfo("Strategy finished successfully！");
                        } else {
                            Logger.logInfo("Strategy finished with errors！");
                        }

                    }
                    catch (Exception e){
                        Logger.logException("Strategy can't finish！");
                        e.printStackTrace();
                    }
                    strategy.writeToFile("strategy_output\\" + apkName);
                }else{
                    Logger.logInfo("Can not get Launchable Activity");
                }

                AdbTool.unInstallApk(agent.getDevice().getSerialNumber(), packageName);
            }

            agent.terminate();
            finishedList.add(apkFilePath);
            Main.setFinishedList("finishedList.txt", finishedList);

            uiautomatorTaskId = AdbTool.getTaskId(device, "uiautomator");
            if(uiautomatorTaskId > 0)
                AdbTool.killTask(device, uiautomatorTaskId);
            Thread.sleep(2000);
        }

        AdbTool.terminateBridge();
        Logger.endLogging();

    }
}
